import {ImageResponse} from "next/og";
export const alt="Medcore hospital operations";
export const size={width:1200,height:630};
export const contentType="image/png";
export default function Image(){return new ImageResponse(<div style={{display:"flex",flexDirection:"column",justifyContent:"space-between",width:"100%",height:"100%",padding:80,background:"#13251c",color:"#c2e8bd"}}><div style={{fontSize:40}}>medcore</div><div style={{display:"flex",flexDirection:"column",fontSize:82,letterSpacing:-4}}><span>More clarity.</span><span>More room for care.</span></div><div style={{fontSize:24,color:"#afc6b4"}}>Hospital operations · Ward planning · Human-confirmed assistance</div></div>,size);}
