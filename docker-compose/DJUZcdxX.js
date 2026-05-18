var l=(e=>(e[e.PRESENTATION=0]="PRESENTATION",e[e.ISSUANCE=1]="ISSUANCE",e))(l||{});const r=new Map([["openid://",0],["openid4vp://",0],["mdoc-openid4vp://",0],["haip://",0],["openid-initiate-issuance://",1],["openid-credential-offer://",1]]);function n(e){return e.replaceAll(`
`,"").trim()}function a(e){return btoa(e).replaceAll("=","").replaceAll("+","-").replaceAll("/","_")}function u(e){
  e=e.replaceAll("-","+").replaceAll("_","/");
  while(e.length%4)e+="=";
  return atob(e)
}function o(e){return s(n(e))!=null}function s(e){e=n(e);for(let[t,i]of r)if(e.startsWith(t))return i;return e.includes("presentationRequests")?0:e.includes("issuanceRequests")?1:null}export{l as S,u as d,a as e,n as f,s as g,o as i};
