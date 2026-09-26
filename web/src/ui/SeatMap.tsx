// Mappa posti: solo la vista studente della mappa pubblicata (niente editor).
import type { ClassmatesResponse, CurrentSeatMapResponse, DeskAssignmentDto } from '@worker/contracts';
import { api } from '../lib/api';
import { getSession } from '../lib/session';
import { Empty, ErrorBox, Loading, PageHeader, formatDate, useAsync } from './common';

function seatsOf(d: DeskAssignmentDto): (string | null)[] {
  const capacity = d.seats ?? 2;
  const ids = [d.studentAId, d.studentBId, d.studentCId ?? null];
  return ids.slice(0, Math.max(capacity, 2));
}

export function SeatMap() {
  const data = useAsync(() =>
    Promise.all([api<CurrentSeatMapResponse>('/api/seat-map/current'), api<ClassmatesResponse>('/api/users')])
  );
  const me = getSession()?.user;

  if (data.loading && !data.data) return <Loading />;
  if (data.error) return <ErrorBox message={data.error} onRetry={data.reload} />;
  const [current, classmates] = data.data!;

  const names = new Map(classmates.users.map((u) => [u.id, `${u.firstName} ${u.lastName.charAt(0)}.`]));
  const map = current.seatMap;
  if (!map || !Array.isArray(map.layout) || map.layout.length === 0) {
    return (
      <section>
        <PageHeader title="Mappa posti" />
        <Empty>Il Rappresentante non ha ancora pubblicato una mappa.</Empty>
      </section>
    );
  }

  const rows = Math.max(...map.layout.map((d) => d.row)) + 1;
  const cols = Math.max(...map.layout.map((d) => d.column)) + 1;
  const byPos = new Map(map.layout.map((d) => [`${d.row}:${d.column}`, d]));
  const myDesk = me ? map.layout.find((d) => seatsOf(d).includes(me.id)) : undefined;

  return (
    <section>
      <PageHeader title="Mappa posti" />
      <p class="muted small">Pubblicata il {formatDate(map.publishedAt)}</p>
      {myDesk ? (
        <p class="card highlight">
          Il tuo posto: fila {myDesk.row + 1}, banco {myDesk.column + 1} da sinistra
          {(() => {
            const mates = seatsOf(myDesk).filter((id) => id && id !== me!.id).map((id) => names.get(id!) ?? '?');
            return mates.length ? `, con ${mates.join(' e ')}` : '';
          })()}
        </p>
      ) : (
        <p class="muted">Non sei in questa mappa.</p>
      )}
      <div class="teacher-desk">Cattedra</div>
      {/* Colonne via CSSOM (style oggetto): ammesso dalla CSP senza 'unsafe-inline'. */}
      <div class="seat-grid" style={{ gridTemplateColumns: `repeat(${cols}, minmax(0, 1fr))` }}>
        {Array.from({ length: rows * cols }, (_, i) => {
          const row = Math.floor(i / cols);
          const column = i % cols;
          const desk = byPos.get(`${row}:${column}`);
          if (!desk) return <div class="desk desk-empty" key={i} />;
          const mine = desk === myDesk;
          return (
            <div class={`desk ${mine ? 'desk-mine' : ''}`} key={i}>
              {seatsOf(desk).map((id, s) => (
                <span class={`seat ${id === me?.id ? 'seat-me' : ''}`} key={s}>
                  {id ? (names.get(id) ?? '?') : '—'}
                </span>
              ))}
            </div>
          );
        })}
      </div>
    </section>
  );
}
