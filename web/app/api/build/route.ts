import { NextResponse } from 'next/server';

const owner = process.env.GITHUB_OWNER || 'Developer0-lab';
const repo = process.env.GITHUB_REPO || 'web2app-builder';
const workflow = 'android-build.yml';

function headers() {
  const token = process.env.GITHUB_TOKEN;
  if (!token) throw new Error('GITHUB_TOKEN is not configured on the server.');
  return {Accept:'application/vnd.github+json',Authorization:`Bearer ${token}`,'X-GitHub-Api-Version':'2026-03-10','Content-Type':'application/json'};
}

export async function POST(req: Request) {
  try {
    const {url, format='both'} = await req.json();
    if (!/^https?:\\/\\/[^\\s]+$/i.test(url)) return NextResponse.json({error:'Invalid website URL.'},{status:400});
    const h = headers();
    const started = Date.now();
    const dispatch = await fetch(`https://api.github.com/repos/${owner}/${repo}/actions/workflows/${workflow}/dispatches`,{method:'POST',headers:h,body:JSON.stringify({ref:'main',inputs:{web_app_url:url}})});
    if (!dispatch.ok) return NextResponse.json({error:`GitHub rejected the build request (${dispatch.status}).`},{status:502});
    let runId:number|undefined;
    for(let i=0;i<8 && !runId;i++){
      await new Promise(r=>setTimeout(r,1200));
      const r=await fetch(`https://api.github.com/repos/${owner}/${repo}/actions/workflows/${workflow}/runs?event=workflow_dispatch&per_page=10`,{headers:h,cache:'no-store'});
      if(!r.ok) continue;
      const data=await r.json();
      const candidate=data.workflow_runs?.find((x:any)=>new Date(x.created_at).getTime()>=started-5000);
      if(candidate) runId=candidate.id;
    }
    if(!runId) return NextResponse.json({error:'Build was triggered, but GitHub did not return the run yet. Refresh and check Actions.'},{status:202});
    return NextResponse.json({runId,status:'queued',format});
  } catch(e) { return NextResponse.json({error:e instanceof Error?e.message:'Server error'},{status:500}); }
}
