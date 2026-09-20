"use client";
import {useState,type ReactNode} from "react";
export interface Column<T>{key:string;label:string;value?:(row:T)=>string|number;render:(row:T)=>ReactNode}
export function DataTable<T>({rows,columns,rowKey,pageSize=20}:{rows:T[];columns:Column<T>[];rowKey:(row:T)=>string|number;pageSize?:number}) {
  const [sort,setSort]=useState<{key:string;desc:boolean}>(),[page,setPage]=useState(0);
  const column=columns.find(c=>c.key===sort?.key),ordered=column?.value ? [...rows].sort((a,b)=>{const av=column.value!(a),bv=column.value!(b);return(typeof av==="number"&&typeof bv==="number" ? av-bv : String(av).localeCompare(String(bv),undefined,{numeric:true}))*(sort?.desc ? -1 : 1);}) : rows;
  const pages=Math.max(1,Math.ceil(rows.length/pageSize)),current=Math.min(page,pages-1);
  return <><table className="responsive-records"><thead><tr>{columns.map(c=><th key={c.key} scope="col" aria-sort={sort?.key===c.key ? sort.desc ? "descending" : "ascending" : undefined}>{c.value ? <button className="table-sort" onClick={()=>{setSort({key:c.key,desc:sort?.key===c.key&&!sort.desc});setPage(0);}}>{c.label}{sort?.key===c.key ? sort.desc ? " ↓" : " ↑" : " ↕"}</button> : c.label}</th>)}</tr></thead><tbody>{ordered.slice(current*pageSize,(current+1)*pageSize).map(row=><tr key={rowKey(row)}>{columns.map(c=><td key={c.key} data-label={c.label}>{c.render(row)}</td>)}</tr>)}</tbody></table><div className="table-pagination"><span>{rows.length} results · Page {current+1} of {pages}</span><button className="secondary" disabled={current===0} onClick={()=>setPage(current-1)}>Previous</button><button className="secondary" disabled={current+1>=pages} onClick={()=>setPage(current+1)}>Next</button></div></>;
}
