import type { MetadataRoute } from "next";
export default function manifest():MetadataRoute.Manifest {
  return {name:"Medcore · Hospital Operations",short_name:"Medcore",description:"Ward operations and your human-confirmed assistant.",start_url:"/app/dashboard",display:"standalone",background_color:"#101a18",theme_color:"#13201b",icons:[{src:"/favicon.svg",sizes:"any",type:"image/svg+xml",purpose:"any"}]};
}
