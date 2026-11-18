import hudson.model.Node
import hudson.slaves.DumbSlave
import hudson.slaves.JNLPLauncher
import hudson.slaves.RetentionStrategy
import jenkins.model.Jenkins

def controller = Jenkins.get()
def name = 'edo1-host'
def agent = controller.getNode(name)
if (agent == null) {
    if (!(controller.numExecutors in [0, 1])) {
        throw new IllegalStateException('Expected zero or one controller executor; inspect before migrating')
    }
    def launcher = new JNLPLauncher()
    launcher.setWebSocket(true)
    agent = new DumbSlave(
        name, 'edo1 existing Jenkins user and deployment tools',
        '/var/lib/jenkins-agent', '1', Node.Mode.NORMAL,
        'edo1 linux docker', launcher,
        new RetentionStrategy.Always(), [])
    controller.addNode(agent)
} else if (agent.remoteFS != '/var/lib/jenkins-agent' || agent.numExecutors != 1 ||
           !(agent.launcher instanceof JNLPLauncher) || !agent.launcher.webSocket ||
           agent.mode != Node.Mode.NORMAL) {
    throw new IllegalStateException('Existing edo1-host differs; refusing to overwrite')
}
controller.numExecutors = 0
controller.save()
println 'edo1-host registered; controller executors=0'
