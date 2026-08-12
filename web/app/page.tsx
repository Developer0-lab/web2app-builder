'use client';

import { useCallback, useEffect, useState } from 'react';

type Artifact = {id:number;name:string;size?:number};
type Build = {runId:number;status:string;conclusion?:string|null;artifacts?:Artifact[];format?:'both'|'apk'|'aab';appName?:string;packageName?:string};

export default function Home(){
 const [url,setUrl]=useState('');
 const [appName,setAppName]=useState('');
 const [packageName,setPackageName]=useState('');
 const [format,setFormat]=useState<'both'|'apk'|'aab'>('both');
 const [build,setBuild]=useState<Build|null>(null);
 const [error,setError]=useState('');
 const [busy,setBusy]=useState(false);
 const [refreshing,setRefreshing]=useState(false);

 const refreshBuild=useCallback(async(runId:number,quiet=false)=>{
   if(!quiet)setRefreshing(true);
   try{
     const r=await fetch(`/api/status/${runId}?t=${Date.now()}`,{cache:'no-store'});
     const data=await r.json();
     if(!r.ok)throw new Error(data.error||'Could not check build status.');
     setBuild((prev)=>({...prev,...data}));
     return data;
   }catch(e){if(!quiet)setError(e instanceof Error?e.message:'Could not refresh build.');return null}
   finally{if(!quiet)setRefreshing(false)}
 },[]);

 async function startBuild(){
  setError('');setBuild(null);
  if(!/^https?:\/\/[^\s]+$/i.test(url.trim())){setError('Enter a valid website URL starting with https://');return;}
  const finalName=appName.trim()||'Web2App';
  const finalPackage=packageName.trim()||'com.web2app.generated';
  setBusy(true);
  try{
    const r=await fetch('/api/build',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({url:url.trim(),format,appName:finalName,packageName:finalPackage})});
    const data=await r.json();
    if(!r.ok)throw new Error(data.error||'Could not start build');
    setBuild(data);
  }catch(e){setError(e instanceof Error?e.message:'Something went wrong')}
  finally{setBusy(false)}
 }

 useEffect(()=>{
   if(!build?.runId||['completed','failure','cancelled'].includes(build.status))return;
   const timer=setInterval(()=>refreshBuild(build.runId,true),4000);
   return()=>clearInterval(timer);
 },[build?.runId,build?.status,refreshBuild]);

 const done=build?.status==='completed'&&build.conclusion==='success';
 const running=!!build&&!done&&!['failure','cancelled'].includes(build.status);
 const apkId=build?.artifacts?.find(a=>a.name==='web2app-apk')?.id;
 const aabId=build?.artifacts?.find(a=>a.name==='web2app-aab')?.id;
 const shownName=build?.appName||appName.trim()||'Web2App';
 const shownPackage=build?.packageName||packageName.trim()||'com.web2app.generated';

 return <main className="shell">
  <section className="hero">
   <span className="badge">ANDROID WEB2APP BUILDER</span>
   <h1>Turn any website into an app.</h1>
   <p>Enter a website, customize the Android app, and get a ready-to-install APK and a Play Store-ready AAB.</p>
  </section>
  <section className="card">
   <label htmlFor="url">Website URL</label>
   <input id="url" className="input" value={url} onChange={e=>setUrl(e.target.value)} placeholder="https://example.com" inputMode="url" autoComplete="url"/>

   <div className="grid2">
    <div><label htmlFor="appName">App name</label><input id="appName" className="input" value={appName} onChange={e=>setAppName(e.target.value)} placeholder="My Website App" maxLength={40}/></div>
    <div><label htmlFor="packageName">Package name</label><input id="packageName" className="input" value={packageName} onChange={e=>setPackageName(e.target.value)} placeholder="com.example.myapp" maxLength={120}/></div>
   </div>

   <label style={{marginTop:20}}>Build format</label>
   <div className="row">{(['both','apk','aab'] as const).map(x=><button type="button" key={x} className={`choice ${format===x?'active':''}`} onClick={()=>setFormat(x)}>{x==='both'?'APK + AAB':x.toUpperCase()}</button>)}</div>
   <button className="btn" onClick={startBuild} disabled={busy}>{busy?'Starting build…':'Build Android App'}</button>

   {error&&<div className="status error">{error}</div>}
   {build&&<div className="status">
    <strong>{done?'Build complete 🎉':build.conclusion==='failure'?'Build failed':build.conclusion==='cancelled'?'Build cancelled':'Building your app…'}</strong>
    <div className="small">{shownName} · {shownPackage}</div>
    <div className="small">Workflow run #{build.runId} · {build.status}{build.conclusion?` · ${build.conclusion}`:''}</div>
    {running&&<div className="progress"><div className="bar"/></div>}
    {done&&<>
      <div className="downloads">
       {(format==='both'||format==='apk')&&(apkId?<a className="download" href={`/api/download/${apkId}?type=apk`}>Download APK</a>:<div className="small">APK is still being indexed. Tap Refresh below.</div>)}
       {(format==='both'||format==='aab')&&(aabId?<a className="download" href={`/api/download/${aabId}?type=aab`}>Download AAB</a>:<div className="small">AAB is still being indexed. Tap Refresh below.</div>)}
      </div>
      <div className="actions"><button className="secondary" type="button" onClick={()=>refreshBuild(build.runId)} disabled={refreshing}>{refreshing?'Refreshing…':'↻ Refresh build'}</button><button className="secondary" type="button" onClick={()=>{setBuild(null);setError('')}}>Build another app</button></div>
    </>}
    {build.conclusion==='failure'&&<div className="actions"><button className="secondary" type="button" onClick={()=>refreshBuild(build.runId)} disabled={refreshing}>{refreshing?'Checking…':'Check again'}</button></div>}
   </div>}
  </section>
 </main>;
}
