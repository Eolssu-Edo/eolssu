import jenkins.model.Jenkins
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition
import org.jenkinsci.plugins.workflow.job.WorkflowJob
import java.time.Instant

// S15P31E102-244: create or update the eolssu-ai Pipeline job from a linted candidate file.
// Usage (edo1, admin CLI): groovy = check|apply  with the candidate at /var/lib/jenkins/eolssu-ai.Jenkinsfile
// (root-installed copy of infra/jenkins/ai.Jenkinsfile, linted with declarative-linter first).
def mode = args ? args[0] : 'check'
assert mode in ['check', 'apply']
def j = Jenkins.get()
def candidate = new File('/var/lib/jenkins/eolssu-ai.Jenkinsfile')
assert candidate.isFile(): 'Candidate Jenkinsfile missing'
def source = candidate.getText('UTF-8')
assert source.contains("agent { label 'edo1-ai' }"): 'Candidate must run on the edo1-ai slot'
assert source.contains('sudo -n /opt/eolssu/bin/deploy-ai'): 'Unexpected candidate'
def backend = j.getItem('eolssu-backend')
assert backend?.definition instanceof CpsFlowDefinition: 'Backend job missing; refusing to guess sandbox mode'
def sandbox = backend.definition.sandbox
assert j.getNode('edo1-ai') != null: 'Register the edo1-ai node first'
def job = j.getItem('eolssu-ai')
assert job == null || job instanceof WorkflowJob: 'eolssu-ai exists and is not a Pipeline job'
def sha = { String s -> java.security.MessageDigest.getInstance('SHA-256').digest(s.getBytes('UTF-8')).encodeHex().toString() }

if (job != null && job.definition instanceof CpsFlowDefinition && job.definition.script == source) {
    println 'eolssu-ai already matches candidate ' + sha(source)
    return
}
if (mode == 'check') {
    println((job == null ? 'WOULD CREATE ' : 'WOULD UPDATE ') + 'eolssu-ai with ' + sha(source))
    return
}
if (job != null) {
    assert !job.building && !job.inQueue: 'eolssu-ai is running or queued'
    def backup = new File('/var/lib/jenkins/ai244-backup-' + Instant.now().toString().replace(':', '-') + '.config.xml')
    backup.text = job.configFile.asString()
    println 'Backed up previous definition to ' + backup
} else {
    job = j.createProject(WorkflowJob, 'eolssu-ai')
}
job.description = 'S15P31E102-244: develop ai 실행 입력이 바뀌면 이미지 빌드·이미지 안 채점 테스트·실행 확인 후 운영 ai 교체(실패 시 이전 이미지로 복구). 전용 슬롯 edo1-ai'
job.definition = new CpsFlowDefinition(source, sandbox)
job.save()
println 'eolssu-ai saved with ' + sha(source)
