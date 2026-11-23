import com.cloudbees.plugins.credentials.CredentialsProvider
import com.cloudbees.plugins.credentials.common.StandardCredentials
import hudson.model.BooleanParameterDefinition
import hudson.model.ParametersDefinitionProperty
import hudson.model.StringParameterDefinition
import jenkins.model.Jenkins
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition
import org.jenkinsci.plugins.workflow.job.WorkflowJob
import java.time.Instant

// S15P31E102-1256: create or update the eolssu-ios-release Pipeline job from a linted candidate file.
// Usage (edo1, admin CLI): groovy = check|apply [candidate]
// candidate defaults to /var/lib/jenkins/eolssu-ios-release.Jenkinsfile (copy of infra/jenkins/ios-release.Jenkinsfile,
// linted with declarative-linter first). The path is read inside the controller container, so keep it under /var/lib/jenkins.
def mode = args ? args[0] : 'check'
assert mode in ['check', 'apply']
def name = 'eolssu-ios-release'
def j = Jenkins.get()
def candidate = new File(args?.size() > 1 ? args[1] : '/var/lib/jenkins/eolssu-ios-release.Jenkinsfile')
assert candidate.isFile(): 'Candidate Jenkinsfile missing'
def source = candidate.getText('UTF-8')
assert source.contains("agent { label 'ios-eunseo' }") && source.contains("IOS_TEAM_ID = 'WDA26H33JA'") &&
    source.contains("method='app-store-connect'"): 'Unexpected candidate'
def backend = j.getItem('eolssu-backend')
assert backend?.definition instanceof CpsFlowDefinition: 'Backend job missing; refusing to guess sandbox mode'
def sandbox = backend.definition.sandbox
assert j.getNode('mac-ios-eunseo')?.labelString == 'ios-eunseo': 'Mac agent node mac-ios-eunseo missing'
def job = j.getItem(name)
assert job == null || job instanceof WorkflowJob: name + ' exists and is not a Pipeline job'
def sha = { String s -> java.security.MessageDigest.getInstance('SHA-256').digest(s.getBytes('UTF-8')).encodeHex().toString() }

// Declarative parameters register only after a run; set them now so "Build with Parameters" matches the candidate.
// Keep this list in the same order and with the same defaults as the parameters block of the Jenkinsfile.
def parameters = [
    new StringParameterDefinition('REVISION', '', 'develop 전체 커밋 SHA'),
    new StringParameterDefinition('MARKETING_VERSION', '', '사용자에게 보이는 버전 CFBundleShortVersionString (예: 1.0.3)'),
    new StringParameterDefinition('CURRENT_PROJECT_VERSION', '', '빌드 번호 CFBundleVersion. App Store Connect에 올린 마지막 빌드보다 큰 정수 (1.0.2는 8)'),
    new BooleanParameterDefinition('UPLOAD_TO_APP_STORE_CONNECT', false, '체크하면 IPA를 만든 뒤 App Store Connect(TestFlight)에 업로드합니다. 심사 제출은 하지 않습니다.'),
]
assert (source =~ /name: '([A-Z_]+)'/).collect { it[1] } == parameters*.name: 'Parameter list differs from candidate'

// IDs only; secret values are never read here.
def credentialIds = CredentialsProvider.lookupCredentials(StandardCredentials, j, null, null)*.id
def missing = ['eolssu-gitlab', 'eolssu-asc-api-key', 'eolssu-asc-key-id', 'eolssu-asc-issuer-id'] - credentialIds
def description = 'App Store 출시용 Release 아카이브·App Store IPA 빌드와 선택적 App Store Connect 업로드(S15P31E102-1256). ' +
    '은서 Mac(ios-eunseo)에서 Credentials eolssu-asc-api-key/-key-id/-issuer-id의 App Store Connect 팀 API 키(Admin)로 클라우드 서명. ' +
    '심사 제출은 하지 않음. 정의 원본: infra/jenkins/ios-release.Jenkinsfile'
def currentParameters = job?.getProperty(ParametersDefinitionProperty)?.parameterDefinitions?.collect { it.name }

if (job != null && job.definition instanceof CpsFlowDefinition && job.definition.script == source &&
        currentParameters == parameters*.name && job.description == description) {
    println name + ' already matches candidate ' + sha(source)
    return
}
if (mode == 'check') {
    println((job == null ? 'WOULD CREATE ' : 'WOULD UPDATE ') + name + ' sandbox=' + sandbox + ' sha256=' + sha(source) +
        ' params=' + parameters*.name + ' currentParams=' + currentParameters + ' missingCredentials=' + missing +
        ' macOnline=' + !j.getComputer('mac-ios-eunseo').offline)
    return
}
assert missing.isEmpty(): 'Register credentials first: ' + missing
if (job != null) {
    assert !job.building && !job.inQueue: name + ' is running or queued'
    def backup = new File('/var/lib/jenkins/ios1256-backup-' + Instant.now().toString().replace(':', '-') + '.config.xml')
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
