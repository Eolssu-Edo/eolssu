const {contextBridge,ipcRenderer}=require('electron');
contextBridge.exposeInMainWorld('updates',{
 tab:page=>ipcRenderer.invoke('updates:tab',page),
 ssh:host=>ipcRenderer.invoke('updates:ssh',host),
 savePlayStore:link=>ipcRenderer.invoke('updates:playstore-save',link),
 connectSite:()=>ipcRenderer.invoke('updates:site-connect'),
 siteBrowser:()=>ipcRenderer.invoke('updates:site-browser'),
 downloadApp:kind=>ipcRenderer.invoke('updates:download-app',kind),
 state:()=>ipcRenderer.invoke('updates:state'),
 chooseProject:()=>ipcRenderer.invoke('updates:choose-project'),
 save:config=>ipcRenderer.invoke('updates:save',config),
 check:()=>ipcRenderer.invoke('updates:check'),
 download:()=>ipcRenderer.invoke('updates:download'),
 install:version=>ipcRenderer.invoke('updates:install',version),
 notificationSettings:()=>ipcRenderer.invoke('updates:notification-settings'),
 testNotification:()=>ipcRenderer.invoke('updates:test-notification'),
 onState:callback=>{const fn=(_,value)=>callback(value);ipcRenderer.on('updates:state',fn);return()=>ipcRenderer.removeListener('updates:state',fn);}
});
