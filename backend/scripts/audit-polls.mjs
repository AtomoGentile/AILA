import { readFileSync, writeFileSync } from 'node:fs';
import { pathToFileURL } from 'node:url';

// Min-cost flow: capacità rispettate, riassegnamenti possibili tramite archi inversi.
export function minimumAssignment(students, slots, cost) {
  const source = 0, studentStart = 1, slotStart = studentStart + students.length;
  const sink = slotStart + slots.length, graph = Array.from({ length: sink + 1 }, () => []);
  function edge(from, to, capacity, price) {
    const f = { to, capacity, price, reverse: graph[to].length };
    const r = { to: from, capacity: 0, price: -price, reverse: graph[from].length };
    graph[from].push(f); graph[to].push(r);
    return f;
  }
  const links = students.map((student, i) => {
    edge(source, studentStart + i, 1, 0);
    return slots.map((slot, j) => edge(studentStart + i, slotStart + j, 1, cost(student, slot)));
  });
  slots.forEach((slot, j) => edge(slotStart + j, sink, slot.capacity, 0));
  let flow = 0, totalCost = 0;
  while (flow < students.length) {
    const distances = Array(graph.length).fill(Infinity), previous = Array(graph.length);
    const queue = [source], queued = new Set([source]); distances[source] = 0;
    for (let index = 0; index < queue.length; index++) {
      const node = queue[index]; queued.delete(node);
      graph[node].forEach((link, linkIndex) => {
        const distance = distances[node] + link.price;
        if (link.capacity > 0 && distance < distances[link.to]) {
          distances[link.to] = distance; previous[link.to] = [node, linkIndex];
          if (!queued.has(link.to)) { queue.push(link.to); queued.add(link.to); }
        }
      });
    }
    if (!Number.isFinite(distances[sink])) break;
    for (let node = sink; node !== source;) {
      const [from, linkIndex] = previous[node], link = graph[from][linkIndex];
      link.capacity--; graph[node][link.reverse].capacity++; node = from;
    }
    flow++; totalCost += distances[sink];
  }
  return { flow, cost: totalCost, assignments: students.flatMap((student, i) => {
    const slotIndex = links[i].findIndex(link => link.capacity === 0);
    return slotIndex < 0 ? [] : [{ studentId: student, slotId: slots[slotIndex].id }];
  }) };
}

export function auditRows(rows) {
  const of = kind => rows.filter(row => row.kind === kind).map(row => JSON.parse(row.data));
  const grids = of('grid'), allSlots = of('slot'), allVotes = of('vote'), allAssignments = of('assignment'), swaps = of('swap');
  return grids.map((grid, pollIndex) => {
    const slots = allSlots.filter(slot => slot.gridId === grid.id);
    const slotIds = new Set(slots.map(s => s.id));
    const assignments = allAssignments.filter(a => slotIds.has(a.slotId));
    const students = [...new Set(assignments.map(a => a.studentId))].sort();
    const votes = new Map(allVotes.filter(v => slotIds.has(v.slotId)).map(v => [`${v.studentId}:${v.slotId}`, v.score]));
    const vote = (studentId, slotId) => votes.get(`${studentId}:${slotId}`);
    const optimum = minimumAssignment(students, slots, (student, slot) => (vote(student, slot.id) ?? 0) < 0 ? 1 : 0);
    const actualNegatives = assignments.filter(a => (vote(a.studentId, a.slotId) ?? 0) < 0).length;
    const nick = id => `S${students.indexOf(id) + 1}`;
    const date = id => slots.find(s => s.id === id)?.date;
    const acceptedSwaps = swaps.filter(s => s.gridId === grid.id).length;
    return {
      poll: pollIndex + 1, subject: grid.subject, createdAt: grid.createdAt,
      survivingStudents: students.length, assignments: assignments.length,
      capacity: slots.reduce((sum, s) => sum + s.capacity, 0),
      assignedNegative: actualNegatives,
      assignedDarkRed: assignments.filter(a => vote(a.studentId, a.slotId) === -300).length,
      assignedWithoutVote: assignments.filter(a => vote(a.studentId, a.slotId) === undefined).length,
      minimumNegative: optimum.flow === students.length ? optimum.cost : null,
      avoidableNegative: optimum.flow === students.length ? actualNegatives - optimum.cost : null,
      acceptedSwaps,
      current: assignments.map(a => ({ student: nick(a.studentId), date: date(a.slotId), vote: vote(a.studentId, a.slotId) ?? null })),
      alternative: actualNegatives > optimum.cost && optimum.flow === students.length ? optimum.assignments.map(a => ({ student: nick(a.studentId), date: date(a.slotId), vote: vote(a.studentId, a.slotId) ?? null })) : [],
      caveat: 'Confronto sulle assegnazioni e sui voti ancora presenti. Bonus iniziali non conservati; eventuali scambi o eliminazioni impediscono di attribuire automaticamente il risultato al calcolo iniziale.'
    };
  });
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  const batches = JSON.parse(readFileSync(process.argv[2], 'utf8'));
  const reports = auditRows(batches.flatMap(batch => batch.results ?? []));
  const output = { generatedAt: new Date().toISOString(), polls: reports };
  writeFileSync(process.argv[3], JSON.stringify(output, null, 2));
  // Nei log solo aggregati, nessun nome né identificativo di studente.
  console.log(JSON.stringify({ polls: reports.length, students: reports.reduce((n, p) => n + p.survivingStudents, 0), negatives: reports.reduce((n, p) => n + p.assignedNegative, 0), avoidableNegatives: reports.reduce((n, p) => n + Math.max(0, p.avoidableNegative ?? 0), 0) }));
}
