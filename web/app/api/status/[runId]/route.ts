import { NextResponse } from 'next/server';

const owner = process.env.GITHUB_OWNER || 'Developer0-lab';
const repo = process.env.GITHUB_REPO || 'web2app-builder';
function headers(){const token=process.env.GITHUB_TOKEN;if(!token)throw new Error('GITHUB_TOKEN is not configured on the server.');return {Accept:'application/vnd.github+json',Authorization:`Bearer ${token}`,'X-GitHub-Api-Version':'2026-03-10'};}

export async function GET(_:Request,{params}:{params:Promise<{runId:string}>}){
  try{
    const {runId}=await params; const h=headers();
    const r=await fetch(`https://api.github.com/repos/${owner}/${repo}/actions/runs/${runId}`,{headers:h,cache:'no-store'});
    if(!r.ok)return NextResponse.json({error:'Workflow run not found.'},{status:r.status});
    const run=await r.json();
    let artifacts:any[]=[];
    if(run.status==='completed'){
      const ar=await fetch(`https://api.github.com/repos/${owner}/${repo}/actions/runs/${runId}/artifacts?per_page=20`,{headers:h,cache:'no-store'});
      if(ar.ok){const d=await ar.json();artifacts=(d.artifacts||[]).filter((a:any)=>!a.expired).map((a:any)=>({id:a.id,name:a.name}));}
    }
    return NextResponse.json({runId:Number(run.id),status:run.status,conclusion:run.conclusion,artifacts});
  }catch(e){return NextResponse.json({error:e instanceof Error?e.message:'Server error'},{status:500});}
}
