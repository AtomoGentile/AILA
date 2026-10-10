// AILA Assistant della gita: risponde solo con il materiale della gita (vedi ai/gita.ts). La
// chiave Google AI Studio è quella personale, salvata su questo dispositivo (Impostazioni).
import { useState } from 'preact/hooks';
import type { GitaCorpusDoc, GitaCorpusResponse } from '@worker/contracts';
import { api } from '../lib/api';
import { kv } from '../lib/db';
import { generateGitaText } from '../ai/gemini';
import { buildGitaPrompt, parseGitaAnswer, type GitaAnswer } from '../ai/gita';

type Message =
  | { role: 'user'; text: string }
  | { role: 'assistant'; answer: GitaAnswer }
  | { role: 'error'; text: string };

/** Oggi nell'ora italiana, come "AAAA-MM-GG". */
function todayIso(): string {
  return new Date().toLocaleDateString('sv-SE', { timeZone: 'Europe/Rome' });
}

export function GitaChat({ onClose }: { onClose: () => void }) {
  const [messages, setMessages] = useState<Message[]>([]);
  const [question, setQuestion] = useState('');
  const [thinking, setThinking] = useState(false);

  async function send(e: Event) {
    e.preventDefault();
    const text = question.trim();
    if (!text || thinking) return;
    setQuestion('');
    setMessages((m) => [...m, { role: 'user', text }]);
    setThinking(true);
    try {
      const [docs, key] = await Promise.all([loadMaterial(), kv.get<string>('geminiKey')]);
      const raw = await generateGitaText(key ?? '', buildGitaPrompt(text, docs, todayIso()));
      setMessages((m) => [...m, { role: 'assistant', answer: parseGitaAnswer(raw, docs) }]);
    } catch (err) {
      setMessages((m) => [...m, { role: 'error', text: err instanceof Error ? err.message : 'Risposta non disponibile.' }]);
    } finally {
      setThinking(false);
    }
  }

  return (
    <div class="gita-chat" role="dialog" aria-modal="true" aria-label="AILA Assistant della gita">
      <header class="gita-chat-head">
        <strong>AILA Assistant · Gita</strong>
        <button class="btn btn-small" onClick={onClose}>Chiudi</button>
      </header>
      <p class="muted gita-chat-note">Risponde solo con il materiale della gita caricato dai rappresentanti.</p>
      <div class="gita-chat-messages" aria-live="polite">
        {messages.length === 0 && <p class="muted">Chiedi, per esempio: «quanto costa la gita?» o «a che ora si parte?».</p>}
        {messages.map((m, i) => (
          <MessageView key={i} message={m} />
        ))}
        {thinking && <p class="muted">Sto leggendo il materiale…</p>}
      </div>
      <form class="gita-chat-input" onSubmit={send}>
        <input
          value={question}
          onInput={(e) => setQuestion(e.currentTarget.value)}
          placeholder="Scrivi una domanda sulla gita"
          aria-label="Domanda sulla gita"
          maxLength={500}
        />
        <button class="btn btn-primary" disabled={thinking || !question.trim()}>Chiedi</button>
      </form>
    </div>
  );
}

function MessageView({ message }: { message: Message }) {
  if (message.role === 'user') return <p class="gita-msg gita-msg-user">{message.text}</p>;
  if (message.role === 'error') return <p class="error">{message.text}</p>;
  return (
    <div class="gita-msg gita-msg-ai">
      <p style={{ whiteSpace: 'pre-line' }}>{message.answer.text}</p>
      {message.answer.sources.length > 0 && (
        <div class="chips">
          {message.answer.sources.map((s) => (
            <span class="chip" key={s.title}>
              Gita - {s.title} (caricato {s.uploadedOn})
            </span>
          ))}
        </div>
      )}
    </div>
  );
}

/** Versioni correnti dei documenti con il testo: una lettura per domanda, come sull'app. */
async function loadMaterial(): Promise<GitaCorpusDoc[]> {
  const res = await api<GitaCorpusResponse>('/api/gita/corpus');
  return res.documents;
}
