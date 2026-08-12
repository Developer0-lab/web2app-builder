'use client';

import { useEffect, useState } from 'react';

type Artifact = {id:number;name:string};
type Build = {runId:number;status:string;conclusion?:string|null;artifacts?:Artifact[]};

export default function Home(){
 const [url,setUrl]=useState(''); const [format,setFormat]=useState<'both'|'apk'|'aab'>('both');
 const [build,setBuild]=useState<Build|null>(null); const [error,setError]=useState(''); const [busy,setBusy]=useState(false);
 async function startBuild(){
  setError('');setBuild(null);
  if(!/^https?:\/\/[^\s]+$/i.test(url)){setError('Enter a valid website URL starting with https://');return;}
  setBusy(true);try{const r=await fetch('/api/build',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({url,format})});const data=await r.json();if(!r.ok)throw new Error(data.error||'Could not start build');setBuild(data);}catch(e){setError(e instanceof Error?e.message:'Something went wrong')}finally{setBusy(false)}
 }
 useEffect(()=>{if(!build?.runId||['completed','failure','cancelled'].includes(build.status))return;const timer=setInterval(async()=>{try{const r=await fetch(`/api/status/${build.runId}`,{cache:'no-store'});const data=await r.json();if(r.ok)setBuild(data);else setError(data.error||'Could not check build status.')}catch{}},4000);return()=>clearInterval(timer)},[build?.runId,build?.status]);
 const done=build?.status==='completed'&&build.conclusion==='success'; const running=!!build&&!done&&!['failure','cancelled'].includes(build.status);
 const apkId=build?.artifacts?.find(a=>a.name==='web2app-apk')?.id; const aabId=build?.artifacts?.find(a=>a.name==='web2app-aab')?.id;
 return <main className="shell"><section className="hero"><span className="badge">ANDROID WEB2APP</span><h1>Turn any website into an app.</h1><p>Enter a website URL and Web2App Builder will create a ready-to-install APK and a Play Store-ready AAB.</p></section><section className="card"><label htmlFor="url">Website URL</label><input id="url" className="input" value={url} onChange={e=>setUrl(e.target.value)} placeholder="https://example.com" inputMode="url"/><label style={{marginTop:20}}>Build format</label><div className="row">{(['both','apk','aab'] as const).map(x=><button type="button" key={x} className={`choice ${format===x?'active':''}`} onClick={()=>setFormat(x)}>{x==='both'?'APK + AAB':x.toUpperCase()}</button>)}</div><button className="btn" onClick={startBuild} disabled={busy}>{busy?'Starting build…':'Build Android App'}</button>{error&&<div className="status error">{error}</div>}{build&&<div className="status"><strong>{done?'Build complete 🎉':build.conclusion==='failure'?'Build failed':'Building your app…'}</strong><div className="small">Workflow run #{build.runId} · {build.status}{build.conclusion?` · ${build.conclusion}`:''}</div>{running&&<div className="progress"><div className="bar"/></div>}{done&&<div className="downloads">{(format==='both'||format==='apk')&&(apkId?<a className="download" href={`/api/download/${apkId}?type=apk`}>Download APK</a>:<div className="small">APK artifact is unavailable. Check the GitHub Actions run.</div>)}{(format==='both'||format==='aab')&&(aabId?<a className="download" href={`/api/download/${aabId}?type=aab`}>Download AAB</a>:<div className="small">AAB artifact is unavailable. Check the GitHub Actions run.</div>)}</div>}</div>}</section></main>;
}
