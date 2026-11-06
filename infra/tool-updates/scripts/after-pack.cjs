const {execFileSync}=require('node:child_process');
const path=require('node:path');
module.exports=async context=>{
 if(context.electronPlatformName==='darwin') {
  const bundle=path.join(context.appOutDir,context.packager.appInfo.productFilename+'.app');
  execFileSync('/usr/bin/codesign',['--force','--deep','--sign','-',bundle],{stdio:'inherit'});
 }
};
