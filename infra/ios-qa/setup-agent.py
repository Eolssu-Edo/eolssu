#!/usr/bin/env python3
"""Install the dedicated Eunseo Jenkins user agent; administrator CLI auth stays on edo1."""
import os,pathlib,plistlib,subprocess,re
home=pathlib.Path.home();assert str(home)=='/Users/o-eunseo', 'This setup targets the approved Eunseo Mac only'; root=home/'jenkins-agent'; root.mkdir(mode=0o700,exist_ok=True);root.chmod(0o700)
ssh=['ssh','-o','ClearAllForwardings=yes','-o','BatchMode=yes','edo1']
groovy='''import jenkins.model.Jenkins
import hudson.model.Node
import hudson.slaves.DumbSlave
import hudson.slaves.JNLPLauncher
import hudson.slaves.RetentionStrategy
def j=Jenkins.get()
def name='mac-ios-eunseo'
def n=j.getNode(name)
if(n==null){
 n=new DumbSlave(name, 'S15P31E102-444: Eunseo paid-team Ad Hoc builder', '/Users/o-eunseo/jenkins-agent', '1', Node.Mode.EXCLUSIVE, 'ios-eunseo', new JNLPLauncher(), new RetentionStrategy.Always(), [])
 j.addNode(n)
}
assert n.remoteFS=='/Users/o-eunseo/jenkins-agent' && n.labelString=='ios-eunseo'
println(j.getComputer(name).getJnlpMac());return null
'''
cmd='sudo -n java -jar /opt/eolssu/jenkins/jenkins-cli.jar -s http://127.0.0.1:8080 -auth @/opt/eolssu/jenkins/admin-cli.auth groovy ='
raw=subprocess.check_output(ssh+[cmd],input=groovy.encode())
matches=re.findall(rb'(?m)^[a-f0-9]{64}$',raw)
assert len(matches)==1, 'Unexpected CLI response shape (secret not shown)'
secret=matches[0]
assert len(secret)==64 and all(c in b'0123456789abcdef' for c in secret)
p=root/'secret';p.write_bytes(secret);p.chmod(0o600)
jar=subprocess.check_output(ssh+['curl --fail --silent http://127.0.0.1:8080/jnlpJars/agent.jar'])
assert jar.startswith(b'PK');(root/'agent.jar').write_bytes(jar)
java=subprocess.check_output(['/usr/libexec/java_home','-v','21'],text=True).strip()+'/bin/java'
launch=home/'Library/LaunchAgents';launch.mkdir(parents=True,exist_ok=True)
configs={
 'com.eolssu.jenkins-tunnel': ['/usr/bin/ssh','-F','/dev/null','-N','-T','-o','BatchMode=yes','-o','ExitOnForwardFailure=yes','-o','ServerAliveInterval=30','-o','ServerAliveCountMax=3','-o','IdentitiesOnly=yes','-i',str(home/'.ssh/K15E102T.pem'),'-L','127.0.0.1:18089:127.0.0.1:8080','ubuntu@k15e102.p.ssafy.io'],
 'com.eolssu.jenkins-agent': [java,'-jar',str(root/'agent.jar'),'-url','http://127.0.0.1:18089/','-secret','@'+str(p),'-name','mac-ios-eunseo','-webSocket','-workDir',str(root)]}
for label,args in configs.items():
 path=launch/(label+'.plist')
 config=dict(Label=label,ProgramArguments=args,RunAtLoad=True,KeepAlive=True,ThrottleInterval=15,StandardOutPath=str(root/(label+'.out.log')),StandardErrorPath=str(root/(label+'.err.log')),EnvironmentVariables={'JAVA_HOME':str(pathlib.Path(java).parent.parent),'PATH':str(pathlib.Path(java).parent)+':/opt/homebrew/bin:/usr/bin:/bin:/usr/sbin:/sbin'})
 if path.exists():
  with path.open('rb') as f: assert plistlib.load(f)==config, 'Existing launch agent differs; inspect before replacing'
  continue
 with path.open('wb') as f: plistlib.dump(config,f)
 subprocess.run(['launchctl','bootstrap','gui/'+str(os.getuid()),str(path)],check=True)
 subprocess.run(['launchctl','kickstart','gui/'+str(os.getuid())+'/'+label],check=True)
print('Created dedicated Jenkins node and started user tunnel/agent; secret stored mode 0600')
