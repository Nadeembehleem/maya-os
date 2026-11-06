// Maya OS brain: zero-dependency Node server (Node 18+). Runs fine in Termux.
const http = require('http'), fs = require('fs'), path = require('path');
try { fs.readFileSync(path.join(__dirname, '.env'), 'utf8').split('\n').forEach(l => {
  const m = l.match(/^\s*([A-Z_]+)\s*=\s*"?([^"\r]*)"?\s*$/); if (m && !process.env[m[1]]) process.env[m[1]] = m[2]; }); } catch {}
const KEY = process.env.GEMINI_API_KEY, MODEL = process.env.GEMINI_MODEL || 'gemini-2.5-flash';
const TOKEN = process.env.MAYA_TOKEN || '', PORT = +process.env.PORT || 3000;

const SYS = `You are Maya, a warm, sharp, Jarvis-style voice assistant living inside the user's Android phone. You can talk AND control the phone.
Reply language: match the user (Hindi / Hinglish / English). Keep "speech" short, natural, spoken aloud (no markdown, no emojis).
Return ONLY JSON: {"speech":"...", "actions":[{"type":"...","params":{...}}]}
Use actions:[] for pure conversation/answers. Chain several actions in order when needed.
Action types and params:
CALL{number} | SEND_SMS{number,text} | WHATSAPP{number,text} | SET_ALARM{hour(0-23),minute,label} | SET_TIMER{seconds,label}
TORCH{state:true|false} | SET_VOLUME{level 0-100} | SET_BRIGHTNESS{level 0-100} | TOGGLE_SETTING{setting:wifi|bluetooth|airplane|dnd|location|nfc|battery_saver,state}
OPEN_APP{app} (app name, e.g. "WhatsApp","YouTube","Camera") | OPEN_URL{url} | NAVIGATE{destination} | PLAY_MUSIC{query} | SEARCH_WEB{query}
SEND_EMAIL{to,subject,body} | ADD_CALENDAR{title,startISO} | OPEN_CAMERA{} | OPEN_SETTINGS{}
INTENT{action,data,package,extras{}} (raw Android intent, advanced) | RUN_CUSTOM{name} (run one of the user's custom actions listed in context)
SAVE_MEMORY{note} (when user says remember / yaad rakhna)
Rules: Resolve contact names to numbers using the user's memory notes. If a needed number/detail is unknown, ask in "speech" and return no actions.
Never invent phone numbers. Prefer the user's custom actions when they match. Use current local time from context for relative times ("kal subah 7 baje").`;

const send = (res, code, obj) => { res.writeHead(code, { 'Content-Type': 'application/json', 'Access-Control-Allow-Origin': '*', 'Access-Control-Allow-Headers': 'Content-Type,x-maya-token', 'Access-Control-Allow-Methods': 'GET,POST,OPTIONS' }); res.end(JSON.stringify(obj)); };

async function think(b) {
  const ctx = { now: new Date().toString(), device: b.device || {}, memory: (b.memory || []).slice(0, 40), customActions: (b.custom || []).map(c => ({ name: c.name, trigger: c.trigger })) };
  const contents = (b.history || []).slice(-10).map(h => ({ role: h.role === 'user' ? 'user' : 'model', parts: [{ text: h.text }] }));
  contents.push({ role: 'user', parts: [{ text: `CONTEXT: ${JSON.stringify(ctx)}\nUSER SAID: ${b.prompt}` }] });
  const r = await fetch(`https://generativelanguage.googleapis.com/v1beta/models/${MODEL}:generateContent`, {
    method: 'POST', headers: { 'Content-Type': 'application/json', 'x-goog-api-key': KEY },
    body: JSON.stringify({ systemInstruction: { parts: [{ text: SYS }] }, contents, generationConfig: { responseMimeType: 'application/json', temperature: 0.6 } }) });
  const j = await r.json();
  if (!r.ok) throw new Error(j.error?.message || 'Gemini error ' + r.status);
  let t = (j.candidates?.[0]?.content?.parts || []).map(p => p.text || '').join('').trim().replace(/^```json\s*|```$/g, '');
  const o = JSON.parse(t); return { speech: String(o.speech || ''), actions: Array.isArray(o.actions) ? o.actions : [] };
}

http.createServer((req, res) => {
  if (req.method === 'OPTIONS') return send(res, 204, {});
  if (req.url === '/health') return send(res, 200, { ok: true, model: MODEL, keySet: !!KEY });
  if (req.method !== 'POST' || req.url !== '/api/command') return send(res, 404, { error: 'not found' });
  if (TOKEN && req.headers['x-maya-token'] !== TOKEN) return send(res, 401, { error: 'bad token' });
  if (!KEY) return send(res, 500, { error: 'GEMINI_API_KEY not set on server' });
  let body = ''; req.on('data', d => { body += d; if (body.length > 2e6) req.destroy(); });
  req.on('end', async () => { try { send(res, 200, await think(JSON.parse(body))); } catch (e) { console.log('ERR', e.message); send(res, 500, { error: e.message }); } });
}).listen(PORT, '0.0.0.0', () => console.log(`Maya brain on :${PORT} model=${MODEL} key=${KEY ? 'set' : 'MISSING'}`));
