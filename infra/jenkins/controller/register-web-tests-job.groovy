import com.cloudbees.plugins.credentials.CredentialsProvider
import com.cloudbees.plugins.credentials.common.StandardCredentials
import hudson.model.ParametersDefinitionProperty
import hudson.model.StringParameterDefinition
import jenkins.model.Jenkins
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition
import org.jenkinsci.plugins.workflow.job.WorkflowJob
import java.time.Instant

// S15P31E102-1270: create or update the eolssu-web-tests Pipeline job from a linted candidate file.
// Usage (edo1, admin CLI): groovy = check|apply [candidate]
// candidate defaults to /var/lib/jenkins/eolssu-web-tests.Jenkinsfile (copy of infra/jenkins/web-tests.Jenkinsfile,
// linted with declarative-linter first). Apply this job before the backend job that queues it.
def mode = args ? args[0] : 'check'
assert mode in ['check', 'apply']
def name = 'eolssu-web-tests'
def j = Jenkins.get()
def candidate = new File(args?.size() > 1 ? args[1] : '/var/lib/jenkins/eolssu-web-tests.Jenkinsfile')
assert candidate.isFile(): 'Candidate Jenkinsfile missing'
def source = candidate.getText('UTF-8')
assert source.contains("agent { label 'edo1-web' }") && source.contains(':shared:wasmJsTest') &&
    source.contains(':shared:jsTest'): 'Unexpected candidate'
assert !source.contains('deploy-frontend') && !source.contains('sudo'): 'Test-only job must not deploy'
def backend = j.getItem('eolssu-backend')
assert backend?.definition instanceof CpsFlowDefinition: 'Backend job missing; refusing to guess sandbox mode'
def sandbox = backend.definition.sandbox
assert j.getNode('edo1-web') != null: 'edo1-web node missing'
def job = j.getItem(name)
assert job == null || job instanceof WorkflowJob: name + ' exists and is not a Pipeline job'
def sha = { String s -> java.security.MessageDigest.getInstance('SHA-256').digest(s.getBytes('UTF-8')).encodeHex().toString() }

// Declarative parameters register only after a run; set them now so the backend job can pass REVISION on the first build.
def parameters = [new StringParameterDefinition('REVISION', '', '백엔드 잡이 넘긴 develop 전체 커밋 SHA')]
assert (source =~ /name: '([A-Z_]+)'/).collect { it[1] } == parameters*.name: 'Parameter list differs from candidate'

// IDs only; secret values are never read here.
def missing = ['eolssu-github'] - CredentialsProvider.lookupCredentials(StandardCredentials, j, null, null)*.id
def description = 'develop 웹 브라우저 테스트(:shared:wasmJsTest·:shared:jsTest)만 실행하고 배포하지 않는다(S15P31E102-1270). ' +
    '웹 빌드 입력이 바뀐 develop 커밋마다 eolssu-backend가 REVISION을 넘겨 예약한다. 정의 원본: infra/jenkins/web-tests.Jenkinsfile'
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
    def backup = new File('/var/lib/jenkins/webtests1270-backup-' + Instant.now().toString().replace(':', '-') + '.config.xml')
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
