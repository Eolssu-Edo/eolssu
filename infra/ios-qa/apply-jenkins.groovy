// Run with existing server admin CLI after declarative-linter succeeds.
// Upload ios.Jenkinsfile as /var/lib/jenkins/ios444-candidate.Jenkinsfile first.
import jenkins.model.Jenkins
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition
import java.time.Instant

def j=Jenkins.get()
def job=j.getItem('eolssu-ios-qa')
assert job != null && job.definition instanceof CpsFlowDefinition
assert !job.isBuilding(): 'Wait until the iOS build is finished'
assert !j.queue.items.any { it.task == job }: 'Wait until queued iOS builds are finished'
assert !j.getComputer('mac-ios-eunseo').offline: 'New Mac must be online'
def source=new File('/var/lib/jenkins/ios444-candidate.Jenkinsfile').text
assert source.contains("label 'ios-eunseo'") && source.contains("IOS_TEAM_ID = 'WDA26H33JA'")
assert !source.contains('Install on iPhone over Wi-Fi')
def backup=new File('/var/lib/jenkins/ios444-backup-'+Instant.now().toString().replace(':','-'))
assert backup.mkdirs()
new File(backup,'ios.config.xml').text=job.configFile.asString()
def previous=job.definition
def backend=j.getItem('eolssu-backend').configFile.asString()
try {
    job.definition=new CpsFlowDefinition(source, true)
    job.save()
    assert job.definition.script==source
    assert j.getItem('eolssu-backend').configFile.asString()==backend
} catch(Exception failure) {
    job.definition=previous;job.save();throw failure
}
println('APPLIED iOS job only; backup='+backup.path)
return null
