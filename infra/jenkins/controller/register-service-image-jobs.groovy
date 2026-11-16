import jenkins.model.Jenkins
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition
import org.jenkinsci.plugins.workflow.job.WorkflowJob
import java.time.Instant

// S15P31E102-951: create or update the eolssu-omr and eolssu-playback Pipeline jobs from one linted candidate file.
// Usage (edo1, admin CLI): groovy = check|apply  with the candidate at /var/lib/jenkins/eolssu-service-image.Jenkinsfile
// (root-installed copy of infra/jenkins/service-image.Jenkinsfile, linted with declarative-linter first).
def mode = args ? args[0] : 'check'
assert mode in ['check', 'apply']
def j = Jenkins.get()
def candidate = new File('/var/lib/jenkins/eolssu-service-image.Jenkinsfile')
assert candidate.isFile(): 'Candidate Jenkinsfile missing'
def source = candidate.getText('UTF-8')
assert source.contains("agent { label 'edo1-ai' }"): 'Candidate must run on the edo1-ai slot'
assert source.contains('sudo -n /opt/eolssu/bin/deploy-service-image'): 'Unexpected candidate'
def backend = j.getItem('eolssu-backend')
assert backend?.definition instanceof CpsFlowDefinition: 'Backend job missing; refusing to guess sandbox mode'
def sandbox = backend.definition.sandbox
assert j.getNode('edo1-ai') != null: 'Register the edo1-ai node first'
def sha = { String s -> java.security.MessageDigest.getInstance('SHA-256').digest(s.getBytes('UTF-8')).encodeHex().toString() }
def descriptions = [
    'eolssu-omr': 'S15P31E102-951: develop omr 이미지 입력이 바뀌면 빌드·이미지 안 OMR 테스트·실행 확인 후 변환 대기열이 빌 때 운영 omr 교체(실패 시 이전 이미지로 복구). 슬롯 edo1-ai',
    'eolssu-playback': 'S15P31E102-951: develop playback 이미지 입력이 바뀌면 빌드·이미지 안 테스트·실행 확인 후 음원 대기열이 빌 때 운영 playback 교체(실패 시 이전 이미지로 복구). 슬롯 edo1-ai',
]

descriptions.each { name, description ->
    def job = j.getItem(name)
    assert job == null || job instanceof WorkflowJob: name + ' exists and is not a Pipeline job'
    if (job != null && job.definition instanceof CpsFlowDefinition && job.definition.script == source) {
        println name + ' already matches candidate ' + sha(source)
        return
    }
    if (mode == 'check') {
        println((job == null ? 'WOULD CREATE ' : 'WOULD UPDATE ') + name + ' with ' + sha(source))
        return
    }
    if (job != null) {
        assert !job.building && !job.inQueue: name + ' is running or queued'
        def backup = new File('/var/lib/jenkins/' + name + '-951-backup-' + Instant.now().toString().replace(':', '-') + '.config.xml')
        backup.text = job.configFile.asString()
        println 'Backed up previous definition to ' + backup
    } else {
        job = j.createProject(WorkflowJob, name)
    }
    job.description = description
    job.definition = new CpsFlowDefinition(source, sandbox)
    job.save()
    println name + ' saved with ' + sha(source)
}
