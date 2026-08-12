import { NextResponse } from 'next/server';

const owner = process.env.GITHUB_OWNER || 'Developer0-lab';
const repo = process.env.GITHUB_REPO || 'web2app-builder';
const workflow = 'android-build.yml';

function headers() {
  const token = process.env.GITHUB_TOKEN;
  if (!token) throw new Error('GITHUB_TOKEN is not configured on the server.');
  return {Accept:'application/vnd.github+json',Authorization:`Bearer ${token}`,'X-GitHub-Api-Version':'2026-03-10','Content-Type':'application/json'};
}

function validPackage(value:string) {
  return /^[A-Za-z_][A-Za-z0-9_]*(\.[A-Za-z_][A-Za-z0-9_]*)+$/.test(value);
}

function validImage(value: unknown, maxChars:number) {
  return typeof value === 'string' && /^[A-Za-z0-9+/=]+$/.test(value) && value.length <= maxChars;
}

export async function POST(req: Request) {
  try {
    const body = await req.json();
    const url = typeof body.url === 'string' ? body.url.trim() : '';
    const format = body.format === 'apk' || body.format === 'aab' ? body.format : 'both';
    const appName = typeof body.appName === 'string' ? body.appName.trim() : 'Web2App';
    const packageName = typeof body.packageName === 'string' ? body.packageName.trim() : 'com.web2app.generated';
    const iconBase64 = body.iconBase64 === '' || body.iconBase64 == null ? '' : body.iconBase64;
    const splashBase64 = body.splashBase64 === '' || body.splashBase64 == null ? '' : body.splashBase64;

    if (!/^https?:\/\/[^\s]+$/i.test(url)) return NextResponse.json({error:'Enter a valid website URL.'},{status:400});
    if (appName.length < 1 || appName.length > 40) return NextResponse.json({error:'App name must be 1–40 characters.'},{status:400});
    if (!validPackage(packageName) || packageName.length > 120) return NextResponse.json({error:'Use a valid Android package name such as com.example.myapp.'},{status:400});
    if (!validImage(iconBase64,18000)) return NextResponse.json({error:'The app icon is too large. Please choose a simpler image.'},{status:400});
    if (!validImage(splashBase64,38000)) return NextResponse.json({error:'The splash screen is too large. Please choose a simpler image.'},{status:400});
    if (iconBase64.length + splashBase64.length > 56000) return NextResponse.json({error:'The icon and splash images are too large together. Please use smaller images.'},{status:400});

    const h = headers();
    const started = Date.now();
    const dispatch = await fetch(`https://api.github.com/repos/${owner}/${repo}/actions/workflows/${workflow}/dispatches`,{method:'POST',headers:h,body:JSON.stringify({ref:'main',inputs:{web_app_url:url,app_name:appName,package_name:packageName,icon_base64:iconBase64,splash_base64:splashBase64}})});
    if (!dispatch.ok) {
      const detail = await dispatch.text().catch(()=> '');
      return NextResponse.json({error:`GitHub rejected the build request (${dispatch.status}).${detail?` ${detail}`:''}`},{status:502});
    }

    let runId:number|undefined;
    for(let i=0;i<10 && !runId;i++){
      await new Promise(r=>setTimeout(r,1200));
      const r=await fetch(`https://api.github.com/repos/${owner}/${repo}/actions/workflows/${workflow}/runs?event=workflow_dispatch&per_page=10`,{headers:h,cache:'no-store'});
      if(!r.ok) continue;
      const data=await r.json();
      const candidate=data.workflow_runs?.find((x:any)=>new Date(x.created_at).getTime()>=started-5000);
      if(candidate) runId=candidate.id;
    }
    if(!runId) return NextResponse.json({error:'Build was triggered, but GitHub did not return the run yet. Refresh and check again.'},{status:202});
    return NextResponse.json({runId,status:'queued',format,appName,packageName});
  } catch(e) { return NextResponse.json({error:e instanceof Error?e.message:'Server error'},{status:500}); }
}
