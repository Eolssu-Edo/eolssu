import jenkins.model.Jenkins
import hudson.model.ParametersDefinitionProperty
import hudson.model.StringParameterDefinition
import org.jenkinsci.plugins.workflow.job.WorkflowJob
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition
import java.time.Instant
import groovy.json.JsonOutput
import groovy.json.JsonSlurper

// prepare writes candidates only. Lint both candidates before invoking apply.
def mode=args ? args[0] : 'prepare'
assert mode in ['prepare','apply']
def j=Jenkins.get()
def backend=j.getItem('eolssu-backend'), frontend=j.getItem('eolssu-frontend')
assert backend?.definition instanceof CpsFlowDefinition
assert frontend?.definition instanceof CpsFlowDefinition
def android=j.getItem('eolssu-android-qa')
assert android==null || android.description?.contains('S15P31E102-312')
def stage="""        stage('Queue Android QA APK') {
            steps {
                build job: 'eolssu-android-qa', parameters: [string(name: 'REVISION', value: env.GIT_COMMIT)], wait: false
            }
        }

"""
def locate={ String source ->
    def matches=(source =~ /(?ms)^        stage\('Queue Android QA APK'\) \{.*?^        \}\r?\n(?:\r?\n)?/)
    def blocks=[]
    while(matches.find()) blocks << [start:matches.start(),end:matches.end(),text:matches.group()]
    assert blocks.size()<=1 && source.count('Queue Android QA APK')==blocks.size(): 'Duplicate or unexpected APK stage'
    if(blocks) assert blocks[0].text.trim().replace('\r\n','\n')==stage.trim(): 'APK stage has unrelated edits'
    blocks ? blocks[0] : null
}
def wireBackend={ String source ->
    def point=source.indexOf("        stage('Queue matching frontend')")
    def deploy=source.indexOf("        stage('Deploy backend transaction')")
    assert deploy>=0 && point>deploy: 'Unexpected backend stage order'
    def old=locate(source)
    if(old) { assert deploy<old.start && old.start<point; return source }
    source.substring(0,point)+stage+source.substring(point)
}
def wireFrontend={ String source ->
    def old=locate(source)
    old ? source.substring(0,old.start)+source.substring(old.end) : source
}
def oldBackend=backend.definition.script, oldFrontend=frontend.definition.script
def newBackend=wireBackend(oldBackend), newFrontend=wireFrontend(oldFrontend)
assert wireBackend(newBackend)==newBackend && wireFrontend(newFrontend)==newFrontend: 'Non-idempotent wiring'
assert newBackend.count('Queue Android QA APK')==1 && !newFrontend.contains('Queue Android QA APK')
def sha={ String s -> java.security.MessageDigest.getInstance('SHA-256').digest(s.getBytes('UTF-8')).encodeHex().toString() }
def root=new File('/var/lib/jenkins')
def bFile=new File(root,'qa312-backend-updated.Jenkinsfile'), fFile=new File(root,'qa312-frontend-updated.Jenkinsfile')
def receiptFile=new File(root,'qa312-hook-candidate.json')
def hashes=[backendBefore:sha(oldBackend),frontendBefore:sha(oldFrontend),backendAfter:sha(newBackend),frontendAfter:sha(newFrontend)]
if(mode=='prepare') {
    bFile.text=newBackend; fFile.text=newFrontend
    receiptFile.text=JsonOutput.toJson(hashes)
    println('PREPARED '+JsonOutput.toJson(hashes)); return
}
assert receiptFile.exists() && new JsonSlurper().parse(receiptFile)==hashes: 'Job changed since preparation; prepare and lint again'
assert sha(bFile.text)==hashes.backendAfter && sha(fFile.text)==hashes.frontendAfter: 'Candidate changed'
// Snapshot all existing definitions before making any persistent job changes.
def backup=new File(root,'qa312-backup-'+Instant.now().toString().replace(':','-'))
assert backup.mkdirs()
new File(backup,'backend.config.xml').text=backend.configFile.asString()
new File(backup,'frontend.config.xml').text=frontend.configFile.asString()
if(android!=null) new File(backup,'android.config.xml').text=android.configFile.asString()
if(android==null) {
    def source=new File(root,'qa312-android.Jenkinsfile').text
    android=j.createProject(WorkflowJob,'eolssu-android-qa')
    android.description='S15P31E102-312: EDO1 develop Android QA APK archive; explicit install in QA web'
    android.definition=new CpsFlowDefinition(source,true)
    android.addProperty(new ParametersDefinitionProperty(new StringParameterDefinition('REVISION','','검증된 develop 전체 SHA')))
    android.save()
}
assert backend.definition.script==oldBackend && frontend.definition.script==oldFrontend: 'Concurrent configuration edit'
try {
    backend.definition=new CpsFlowDefinition(newBackend,backend.definition.sandbox); backend.save()
    frontend.definition=new CpsFlowDefinition(newFrontend,frontend.definition.sandbox); frontend.save()
} catch(Exception failure) {
    // Restore both definitions if a save fails; do not leave duplicate triggering in place.
    backend.definition=new CpsFlowDefinition(oldBackend,backend.definition.sandbox); backend.save()
    frontend.definition=new CpsFlowDefinition(oldFrontend,frontend.definition.sandbox); frontend.save()
    throw failure
}
assert sha(backend.definition.script)==hashes.backendAfter && sha(frontend.definition.script)==hashes.frontendAfter
println('APPLIED backend deploy -> Android queue -> web queue; backup='+backup.path)
