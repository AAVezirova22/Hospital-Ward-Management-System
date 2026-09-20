"use client";
import {useEffect,useState} from "react";
import {Bell} from "lucide-react";
import {useQuery} from "@tanstack/react-query";
import {api} from "../api";
import type {RoomCapacity} from "../api/contracts";
export function NotificationCenter(){const[open,setOpen]=useState(false),[notices,setNotices]=useState<string[]>([]);const rooms=useQuery({queryKey:["/rooms"],queryFn:()=>api<RoomCapacity[]>("/rooms"),refetchInterval:30000});useEffect(()=>{const handler=(e:Event)=>setNotices(n=>[String((e as CustomEvent).detail),...n].slice(0,6));window.addEventListener("saved",handler);return()=>window.removeEventListener("saved",handler);},[]);const full=(rooms.data??[]).filter(r=>r.active&&r.availableBeds===0);return <div className="notification-center"><button className="icon" aria-label={`Notifications (${full.length+notices.length})`} aria-expanded={open} onClick={()=>setOpen(!open)}><Bell size={18}/></button>{open&&<section className="panel notification-panel"><h3>Notifications</h3>{full.map(r=><p key={r.id}>Room {r.roomNumber} is at capacity.</p>)}{notices.map((n,i)=><p key={i}>{n}</p>)}{!full.length&&!notices.length&&<p>No capacity warnings or recent actions.</p>}<button className="text-button" onClick={()=>{setNotices([]);setOpen(false);}}>Dismiss</button></section>}</div>;}
