import com.cloudbees.plugins.credentials.CredentialsProvider
import com.cloudbees.plugins.credentials.common.StandardCredentials
import hudson.model.ChoiceParameterDefinition
import hudson.model.ParametersDefinitionProperty
import hudson.model.StringParameterDefinition
import hudson.model.TextParameterDefinition
import jenkins.model.Jenkins
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition
import org.jenkinsci.plugins.workflow.job.WorkflowJob
import java.time.Instant

// S15P31E102-1254: create or update the eolssu-android-release Pipeline job from a linted candidate file.
// Usage (edo1, admin CLI): groovy = check|apply [candidate]
// candidate defaults to /var/lib/jenkins/eolssu-android-release.Jenkinsfile (copy of infra/jenkins/android-release.Jenkinsfile,
// linted with declarative-linter first). The path is read inside the controller container, so keep it under /var/lib/jenkins.
def mode = args ? args[0] : 'check'
assert mode in ['check', 'apply']
def name = 'eolssu-android-release'
def j = Jenkins.get()
def candidate = new File(args?.size() > 1 ? args[1] : '/var/lib/jenkins/eolssu-android-release.Jenkinsfile')
assert candidate.isFile(): 'Candidate Jenkinsfile missing'
def source = candidate.getText('UTF-8')
assert source.contains("agent { label 'edo1-host' }") && source.contains(':androidApp:bundleRelease'): 'Unexpected candidate'
def backend = j.getItem('eolssu-backend')
assert backend?.definition instanceof CpsFlowDefinition: 'Backend job missing; refusing to guess sandbox mode'
def sandbox = backend.definition.sandbox
assert j.getNode('edo1-host') != null: 'edo1-host node missing'
def job = j.getItem(name)
assert job == null || job instanceof WorkflowJob: name + ' exists and is not a Pipeline job'
def sha = { String s -> java.security.MessageDigest.getInstance('SHA-256').digest(s.getBytes('UTF-8')).encodeHex().toString() }

// Declarative parameters register only after a run; set them now so "Build with Parameters" matches the candidate.
// Keep this list in the same order and with the same defaults as the parameters block of the Jenkinsfile.
def parameters = [
    new StringParameterDefinition('REVISION', '', 'develop 전체 커밋 SHA'),
    new StringParameterDefinition('VERSION_CODE', '', 'Play versionCode (이전 업로드보다 큰 정수)'),
    new StringParameterDefinition('VERSION_NAME', '', '사용자에게 보이는 버전 (예: 1.3.0)'),
]
if (source.contains("name: 'PLAY_TRACK'")) {
    parameters += [
        new ChoiceParameterDefinition('PLAY_TRACK', ['production', 'internal', 'none'] as String[], 'AAB를 올릴 Google Play 트랙. none이면 AAB만 만들고 올리지 않습니다.'),
        new ChoiceParameterDefinition('PLAY_STATUS', ['draft', 'completed'] as String[], 'draft: 초안으로 올리고 Play Console에서 사람이 검토 전송합니다. completed: 올리면서 바로 검토 전송합니다(승인되면 100% 출시).'),
        new TextParameterDefinition('RELEASE_NOTES_KO', '', 'ko-KR 출시 노트(500자 이하). 비우면 노트 없이 올립니다.'),
    ]
}
parameters.each { p -> assert source.contains("name: '${p.name}'"): 'Candidate lacks parameter ' + p.name }
assert (source =~ /name: '([A-Z_]+)'/).collect { it[1] } == parameters*.name: 'Parameter list differs from candidate'

// IDs only; secret values are never read here.
def credentialIds = CredentialsProvider.lookupCredentials(StandardCredentials, j, null, null)*.id
def needed = ['eolssu-gitlab', 'eolssu-android-upload-keystore', 'eolssu-android-upload-password']
if (source.contains('eolssu-play-service-account')) { needed << 'eolssu-play-service-account' }
def missing = needed - credentialIds
def description = 'Play 스토어 출시용 서명 AAB 빌드(테스트·lint 포함)와 Google Play 업로드(S15P31E102-1254). ' +
    '업로드 키는 Credentials eolssu-android-upload-keystore/-password, Play 업로드는 서비스 계정 eolssu-play-service-account. ' +
    '정의 원본: infra/jenkins/android-release.Jenkinsfile'
def currentParameters = job?.getProperty(ParametersDefinitionProperty)?.parameterDefinitions?.collect { it.name }

if (job != null && job.definition instanceof CpsFlowDefinition && job.definition.script == source &&
        currentParameters == parameters*.name && job.description == description) {
    println name + ' already matches candidate ' + sha(source)
    return
}
if (mode == 'check') {
    println((job == null ? 'WOULD CREATE ' : 'WOULD UPDATE ') + name + ' sandbox=' + sandbox + ' sha256=' + sha(source) +
        ' params=' + parameters*.name + ' currentParams=' + currentParameters + ' missingCredentials=' + missing)
    return
}
assert missing.isEmpty(): 'Register credentials first: ' + missing
if (job != null) {
    assert !job.building && !job.inQueue: name + ' is running or queued'
    def backup = new File('/var/lib/jenkins/android1254-backup-' + Instant.now().toString().replace(':', '-') + '.config.xml')
    backup.text = job.configFile.asString()
    println 'Backed up previous definition to ' + backup
} else {
    job = j.createProject(WorkflowJob, name)
}
job.description = description
job.definition = new CpsFlowDefinition(source, sandbox)
job.removeProperty(ParametersDefinitionProperty)
job.addProperty(new ParametersDefinitionProperty(parameters))
job.save()
println name + ' saved with ' + sha(source) + ' params=' + job.getProperty(ParametersDefinitionProperty).parameterDefinitionNames
