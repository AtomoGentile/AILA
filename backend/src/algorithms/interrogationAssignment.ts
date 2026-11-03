// Assegnazione globale: stessi punteggi, ma considera anche gli scambi e le catene di spostamenti.
// Ogni cammino aumenta il flusso di uno; gli archi inversi possono correggere scelte precedenti.
interface Edge { to: number; capacity: number; price: number; movement: number; reverse: number }

export function maximizeAssignment(
  students: string[], slots: Array<{ id: string; capacity: number }>,
  score: (student: string, slot: { id: string; capacity: number }) => number,
  preferredSlots?: ReadonlyMap<string, string>
) {
  const source = 0, studentStart = 1, slotStart = studentStart + students.length;
  const sink = slotStart + slots.length, graph = Array.from({ length: sink + 1 }, (): Edge[] => []);
  function edge(from: number, to: number, capacity: number, price: number, movement = 0) {
    const f = { to, capacity, price, movement, reverse: graph[to].length };
    const r = { to: from, capacity: 0, price: -price, movement: -movement, reverse: graph[from].length };
    graph[from].push(f); graph[to].push(r);
    return f;
  }
  const links = students.map((student, i) => {
    edge(source, studentStart + i, 1, 0);
    return slots.map((slot, j) => edge(studentStart + i, slotStart + j, 1, -score(student, slot), preferredSlots && preferredSlots.get(student) !== slot.id ? 1 : 0));
  });
  slots.forEach((slot, j) => edge(slotStart + j, sink, slot.capacity, 0));
  let flow = 0, totalCost = 0;
  while (flow < students.length) {
    const distances = Array(graph.length).fill(Infinity), movements = Array(graph.length).fill(Infinity), previous: Array<[number, number]> = Array(graph.length);
    const queue = [source], queued = new Set([source]); distances[source] = 0; movements[source] = 0;
    for (let index = 0; index < queue.length; index++) {
      const node = queue[index]; queued.delete(node);
      graph[node].forEach((link, linkIndex) => {
        const distance = distances[node] + link.price;
        const movement = movements[node] + link.movement;
        if (link.capacity > 0 && (distance < distances[link.to] - 1e-9 ||
            (Math.abs(distance - distances[link.to]) <= 1e-9 && movement < movements[link.to]))) {
          movements[link.to] = movement;
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
  return { flow, score: -totalCost, assignments: students.flatMap((student, i) => {
    const slotIndex = links[i].findIndex(link => link.capacity === 0);
    return slotIndex < 0 ? [] : [{ studentId: student, slotId: slots[slotIndex].id }];
  }) };
}
