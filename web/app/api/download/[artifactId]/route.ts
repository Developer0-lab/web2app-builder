import { NextResponse } from 'next/server';
import JSZip from 'jszip';

const owner=process.env.GITHUB_OWNER||'Developer0-lab';
const repo=process.env.GITHUB_REPO||'web2app-builder';
function headers(){const token=process.env.GITHUB_TOKEN;if(!token)throw new Error('GITHUB_TOKEN is not configured on the server.');return {Accept:'application/vnd.github+json',Authorization:`Bearer ${token}`,'X-GitHub-Api-Version':'2026-03-10'};}

export async function GET(req:Request,{params}:{params:Promise<{artifactId:string}>}){
 try{
  const {artifactId}=await params; const type=new URL(req.url).searchParams.get('type')||'apk';
  if(!/^\\d+$/.test(artifactId))return NextResponse.json({error:'Invalid artifact.'},{status:400});
  const r=await fetch(`https://api.github.com/repos/${owner}/${repo}/actions/artifacts/${artifactId}/zip`,{headers:headers(),cache:'no-store'});
  if(!r.ok)return NextResponse.json({error:'Artifact is unavailable or expired.'},{status:r.status});
  const zip=await JSZip.loadAsync(await r.arrayBuffer());
  const wanted=type==='aab'?'.aab':'.apk';
  const entry=Object.values(zip.files).find((f:any)=>f.name.toLowerCase().endsWith(wanted)&&!f.dir) as any;
  if(!entry)return NextResponse.json({error:`${type.toUpperCase()} file was not found in the artifact.`},{status:404});
  const bytes=await entry.async('uint8array');
  return new NextResponse(bytes as any,{headers:{'Content-Type':type==='aab'?'application/octet-stream':'application/vnd.android.package-archive','Content-Disposition':`attachment; filename="web2app.${type}"`,'Cache-Control':'private, no-store'}});
 }catch(e){return NextResponse.json({error:e instanceof Error?e.message:'Download failed'},{status:500});}
}
