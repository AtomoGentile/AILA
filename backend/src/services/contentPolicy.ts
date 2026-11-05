import { unzipSync, strFromU8 } from 'fflate';
import { HTTPException } from 'hono/http-exception';

export const CONTENT_POLICY = {
 smallThreshold:3*1024*1024, smallDays:90, largeDays:7,
 imageBytes:10*1024*1024, documentBytes:20*1024*1024, audioBytes:20*1024*1024,
 audioSeconds:600, imagePixels:24_000_000, textBytes:2*1024*1024,
} as const;
const DOCX='application/vnd.openxmlformats-officedocument.wordprocessingml.document';
function bad(message:string):never{throw new HTTPException(400,{message});}
const ascii=(b:Uint8Array,start=0,end=b.length)=>new TextDecoder('latin1').decode(b.subarray(start,end));
function xmlText(text:string){return text.replace(/<\/w:p>/g,'\n').replace(/<[^>]+>/g,' ').replace(/&amp;/g,'&').replace(/&lt;/g,'<').replace(/&gt;/g,'>').replace(/&quot;/g,'"').replace(/\s+\n/g,'\n').trim();}

/** Metadata only, bounded by the upload limit. Reject audio whose duration cannot be verified. */
export function audioDuration(bytes:Uint8Array,mime:string):number|null {
 const view=new DataView(bytes.buffer,bytes.byteOffset,bytes.byteLength);
 if(mime==='audio/wav'&&ascii(bytes,0,4)==='RIFF'&&ascii(bytes,8,12)==='WAVE'){
  let rate=0;let data=0;
  for(let pos=12;pos+8<=bytes.length;){const size=view.getUint32(pos+4,true);const type=ascii(bytes,pos,pos+4);
   if(pos+8+size>bytes.length)return null;
   if(type==='fmt '&&size>=16)rate=view.getUint32(pos+16,true);
   if(type==='data')data+=size;pos+=8+size+(size%2);
  }return rate>0&&data>0?data/rate:null;
 }
 if(mime==='audio/mp4'){
  function boxes(start:number,end:number,depth:number):number|null{
   if(depth>8)return null;
   for(let pos=start;pos+8<=end;){
    let size=view.getUint32(pos);let header=8;
    if(size===1){if(pos+16>end)return null;const large=view.getBigUint64(pos+8);if(large>BigInt(Number.MAX_SAFE_INTEGER))return null;size=Number(large);header=16;}
    else if(size===0)size=end-pos;
    if(size<header||pos+size>end)return null;
    const type=ascii(bytes,pos+4,pos+8);const data=pos+header;
    if((type==='mvhd'||type==='mdhd')&&data+20<=pos+size){
     const version=bytes[data];if(version!==0&&version!==1)return null;
     const scaleOffset=data+(version===1?20:12);const durationOffset=scaleOffset+4;
     if(durationOffset+(version===1?8:4)>pos+size)return null;
     const scale=view.getUint32(scaleOffset);
     const duration=version===1?view.getBigUint64(durationOffset):BigInt(view.getUint32(durationOffset));
     const unknown=version===1?0xffffffffffffffffn:0xffffffffn;
     if(scale>0&&duration>0n&&duration!==unknown)return Number(duration)/scale;
    }
    if(['moov','trak','mdia'].includes(type)){const d=boxes(data,pos+size,depth+1);if(d!==null)return d;}
    pos+=size;
   }return null;
  }return boxes(0,bytes.length,0);
 }
 if(mime==='audio/ogg'){
  const header=ascii(bytes,0,Math.min(bytes.length,512));const opus=header.indexOf('OpusHead');
  const vorbis=header.indexOf('vorbis');let rate=opus>=0?48000:vorbis>=0&&vorbis+15<bytes.length?view.getUint32(vorbis+11,true):0;
  let last=0n;let pos=0;
  while(pos+27<=bytes.length){if(ascii(bytes,pos,pos+4)!=='OggS')return null;const segments=bytes[pos+26];
   if(pos+27+segments>bytes.length)return null;const granule=view.getBigUint64(pos+6,true);if(granule!==0xffffffffffffffffn)last=granule;
   let size=0;for(let i=0;i<segments;i++)size+=bytes[pos+27+i];pos+=27+segments+size;
  }return rate>0&&last>0n?Number(last)/rate:null;
 }
 if(mime==='audio/mpeg'){
  // Walk every frame; estimates based on file size would accept malformed/VBR durations.
  let pos=0;if(ascii(bytes,0,3)==='ID3'&&bytes.length>=10)pos=10+((bytes[6]&127)<<21|(bytes[7]&127)<<14|(bytes[8]&127)<<7|bytes[9]&127);
  let seconds=0;let frames=0;
  while(pos+4<=bytes.length){if(ascii(bytes,pos,pos+3)==='TAG'&&bytes.length-pos===128)break;
   const h=view.getUint32(pos);if((h>>>21)!==0x7ff)return null;
   const version=(h>>>19)&3;const layer=(h>>>17)&3;const bit=(h>>>12)&15;const sr=(h>>>10)&3;
   if(version===1||layer!==1||bit===0||bit===15||sr===3)return null;
   const bitrate=(version===3?[0,32,40,48,56,64,80,96,112,128,160,192,224,256,320]:[0,8,16,24,32,40,48,56,64,80,96,112,128,144,160])[bit]*1000;
   const rate=[44100,48000,32000][sr]/(version===3?1:version===2?2:4);
   const samples=version===3?1152:576;const length=Math.floor((version===3?144:72)*bitrate/rate)+((h>>>9)&1);
   if(length<4||pos+length>bytes.length)return null;seconds+=samples/rate;frames++;pos+=length;
  }return frames>0?seconds:null;
 }
 return null;
}

function imageDimensions(bytes:Uint8Array,mime:string):[number,number]|null {
 const v=new DataView(bytes.buffer,bytes.byteOffset,bytes.byteLength);
 if(mime==='image/png'&&bytes.length>=24)return [v.getUint32(16),v.getUint32(20)];
 if(mime==='image/jpeg'){
  let pos=2;
  while(pos+4<=bytes.length){
   if(bytes[pos++]!==255)return null;
   while(pos<bytes.length&&bytes[pos]===255)pos++;
   const marker=bytes[pos++];if(marker===0xd9||marker===0xda)return null;
   if(marker===0x01||marker>=0xd0&&marker<=0xd7)continue;
   if(pos+2>bytes.length)return null;const length=v.getUint16(pos);
   if(length<2||pos+length>bytes.length)return null;
   if([0xc0,0xc1,0xc2,0xc3,0xc5,0xc6,0xc7,0xc9,0xca,0xcb,0xcd,0xce,0xcf].includes(marker)){
    if(length<8)return null;return [v.getUint16(pos+5),v.getUint16(pos+3)];
   }pos+=length;
  }
 }
 if(mime==='image/webp'&&bytes.length>=30){
  const type=ascii(bytes,12,16);
  if(type==='VP8X')return [1+bytes[24]+(bytes[25]<<8)+(bytes[26]<<16),1+bytes[27]+(bytes[28]<<8)+(bytes[29]<<16)];
  if(type==='VP8 '&&bytes[23]===0x9d&&bytes[24]===1&&bytes[25]===0x2a)return [v.getUint16(26,true)&0x3fff,v.getUint16(28,true)&0x3fff];
  if(type==='VP8L'&&bytes[20]===0x2f){const bits=v.getUint32(21,true);return [(bits&0x3fff)+1,((bits>>>14)&0x3fff)+1];}
 }
 return null;
}

export function validateContent(name:string,declaredMime:string,bytes:Uint8Array):{mime:string;text:string;duration:number|null} {
 if(!bytes.length)bad('File vuoto');
 const ext=name.toLowerCase().split('.').pop();const declared=declaredMime.split(';')[0].toLowerCase();
 let mime='';let text='';let duration:number|null=null;
 if(ext==='pdf'&&ascii(bytes,0,5)==='%PDF-'){
  mime='application/pdf';if(bytes.length>CONTENT_POLICY.documentBytes)bad('PDF: massimo 20 MB');
  if(/\/(?:JavaScript|JS|Launch|EmbeddedFile|RichMedia|OpenAction)\b/.test(ascii(bytes)))bad('Il PDF contiene azioni o contenuti incorporati non consentiti');
 }else if(ext==='txt'&&['text/plain','application/octet-stream',''].includes(declared)){
  mime='text/plain';if(bytes.length>CONTENT_POLICY.textBytes)bad('Testo: massimo 2 MB');
  try{text=new TextDecoder('utf-8',{fatal:true,ignoreBOM:false}).decode(bytes);}catch{bad('Il testo deve essere UTF-8');}
  if(text.includes('\0'))bad('File di testo non valido');
 }else if(ext==='docx'&&bytes[0]===80&&bytes[1]===75){
  mime=DOCX;if(bytes.length>10*1024*1024)bad('Documento: massimo 10 MB');
  let expanded=0;let entries=0;
  const files=unzipSync(bytes,{filter:file=>{
   entries++;expanded+=file.originalSize;
   if(entries>300||expanded>30*1024*1024||file.originalSize>5*1024*1024)bad('Documento compresso troppo complesso');
   if(/vbaProject|embeddings\/|\.bin$|\.exe$/i.test(file.name))bad('Macro e oggetti incorporati non consentiti');
   return file.name==='word/document.xml';
  }});
  if(!files['word/document.xml'])bad('Documento Word non valido');
  const xml=strFromU8(files['word/document.xml']);if(/<!DOCTYPE|<!ENTITY/i.test(xml))bad('Entità XML non consentite');text=xmlText(xml).slice(0,100_000);
 }else if(ext==='png'&&[137,80,78,71,13,10,26,10].every((n,i)=>bytes[i]===n)&&bytes.length>=24){
  mime='image/png';const v=new DataView(bytes.buffer,bytes.byteOffset,bytes.byteLength);
  if(v.getUint32(16)*v.getUint32(20)>CONTENT_POLICY.imagePixels)bad('Immagine troppo grande (massimo 24 megapixel)');
 }else if(['jpg','jpeg'].includes(ext??'')&&bytes[0]===255&&bytes[1]===216&&bytes[bytes.length-2]===255&&bytes[bytes.length-1]===217){mime='image/jpeg';}
 else if(ext==='webp'&&ascii(bytes,0,4)==='RIFF'&&ascii(bytes,8,12)==='WEBP'){mime='image/webp';}
 else if(['wav','m4a','mp3','ogg'].includes(ext??'')){
  mime=ext==='wav'?'audio/wav':ext==='m4a'?'audio/mp4':ext==='mp3'?'audio/mpeg':'audio/ogg';
  if(bytes.length>CONTENT_POLICY.audioBytes)bad('Audio: massimo 20 MB');
  duration=audioDuration(bytes,mime);if(duration===null||!Number.isFinite(duration)||duration<=0)bad('Durata audio non verificabile: usa WAV, M4A, MP3 o OGG validi');
  if(duration>CONTENT_POLICY.audioSeconds)bad('Audio: massimo 10 minuti');
 }else bad('Formato non supportato. Usa immagini PNG/JPEG/WebP, PDF, DOCX, TXT o audio WAV/M4A/MP3/OGG.');
 if(mime.startsWith('image/')){
  if(bytes.length>CONTENT_POLICY.imageBytes)bad('Immagine: massimo 10 MB');
  const dimensions=imageDimensions(bytes,mime);
  if(!dimensions||dimensions[0]<=0||dimensions[1]<=0)bad('Dimensioni immagine non verificabili');
  if(dimensions[0]*dimensions[1]>CONTENT_POLICY.imagePixels)bad('Immagine troppo grande (massimo 24 megapixel)');
 }
 return {mime,text,duration};
}

export function attachmentExpiry(size:number,smallDays=90,now=Date.now()):string{
 const days=size>CONTENT_POLICY.smallThreshold?7:smallDays;
 return new Date(now+days*86_400_000).toISOString();
}
