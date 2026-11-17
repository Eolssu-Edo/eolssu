export function apkVersionState(catalog) {
  if(!catalog||catalog.installedStatus==='checking'||catalog.installedStatus==='installing')return {status:'checking'};
  const build=catalog.builds?.find(value=>value.id===catalog.latest);
  if(!build||catalog.sync?.error||!['installed','absent'].includes(catalog.installedStatus))return {status:'unavailable'};
  const installed=catalog.installed;
  return {status:installed?.package===build.package&&installed.versionCode===build.versionCode&&installed.versionName===build.versionName?'latest':'outdated',build};
}

export function latestApkUpdate(catalog, state, pending = false) {
  const build = catalog?.builds?.find(b => b.id === catalog.latest);
  const blocked = hint => ({build, enabled:false, label:'최신 APK로 업데이트', hint});
  if (pending || catalog?.job?.status === 'installing' || state?.maintenance) {
    return {...blocked('설치가 끝날 때까지 이 기기의 조작을 잠급니다.'), label:'설치 중…'};
  }
  if (!build) return blocked('최신 APK 목록을 확인하고 있습니다.');
  if (catalog.sync?.error) return blocked('APK 목록 동기화를 확인해 주세요.');
  if (!['installed','absent'].includes(catalog.installedStatus)) return blocked('기기의 설치 정보를 확인한 뒤 업데이트할 수 있습니다.');
  const installed = catalog.installed;
  if (installed?.package === build.package && installed.versionCode === build.versionCode && installed.versionName === build.versionName) {
    return {...blocked('기기에 최신 보관 APK가 설치되어 있습니다.'), label:'최신 버전 사용 중'};
  }
  if (!state?.online || state.inputFault) return blocked('기기 연결이 정상일 때 업데이트할 수 있습니다.');
  if (state.owner !== state.you) return blocked('헤더에서 제어권을 받은 뒤 업데이트할 수 있습니다.');
  return {build, enabled:true, label:'최신 APK로 업데이트', hint:'최신 보관 APK를 설치합니다. 실행 중인 앱은 종료될 수 있습니다.'};
}
