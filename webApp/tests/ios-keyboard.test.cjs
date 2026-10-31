// Test della sequenza DOM; la tastiera di sistema va verificata su iPhone/iPad.
// Eseguire: node --test webApp/tests/ios-keyboard.test.cjs
const { test } = require('node:test');
const assert = require('node:assert/strict');
const { readFileSync } = require('node:fs');
const vm = require('node:vm');
const source = readFileSync(require('node:path').join(__dirname,
  '../src/wasmJsMain/resources/ios-keyboard.js'), 'utf8');

function setup({ ios = true, search = '', input = true } = {}) {
  const listeners = new Map();
  const timers = new Map();
  let clock = 0;
  let nextTimer = 0;
  let doc;
  class Element {
    constructor(tag, root) {
      this.tagName = tag.toUpperCase();
      this.root = root;
      this.style = {};
      this.isConnected = true;
      this.focusCalls = 0;
      this.blurCalls = 0;
    }
    setAttribute() {}
    getRootNode() { return this.root || doc; }
    focus() {
      this.focusCalls++;
      const r = this.getRootNode();
      r.activeElement = this;
      doc.activeElement = r.host || this;
      if (r.onFocus) r.onFocus();
    }
    blur() {
      this.blurCalls++;
      this.getRootNode().activeElement = null;
      doc.activeElement = null;
    }
  }
  const box = { left: 20, top: 100, right: 220, bottom: 150, width: 200, height: 50 };
  const root = {
    host: { isConnected: true }, activeElement: null,
    appendChild() {},
    querySelector() { return field; },
    querySelectorAll() { return [{ getBoundingClientRect: () => box }]; },
    addEventListener(type, fn) { this.onFocus = fn; },
    removeEventListener(type, fn) { if (this.onFocus === fn) this.onFocus = null; },
  };
  let field = input ? new Element('input', root) : null;
  const children = [];
  doc = {
    activeElement: null,
    body: {
      querySelectorAll: () => [{ shadowRoot: root }],
      appendChild: el => children.push(el),
    },
    createElement: tag => new Element(tag),
    addEventListener(type, fn) { listeners.set(type, fn); },
  };
  const window = { innerHeight: 800, visualViewport: { height: 800 } };
  vm.runInNewContext(source, {
    navigator: { userAgent: ios ? 'iPhone' : 'Android', platform: '', maxTouchPoints: 1 },
    location: { search }, HTMLElement: Element, document: doc, window,
    Date: { now: () => clock },
    setInterval() { return 1; }, clearInterval() {},
    setTimeout(fn, delay) { const id = ++nextTimer; timers.set(id, { fn, at: clock + delay }); return id; },
    clearTimeout(id) { timers.delete(id); },
  });
  function event(type, props = {}) {
    const e = { pointerType: 'touch', clientX: 50, clientY: 120, cancelable: true,
      prevented: false, preventDefault() { this.prevented = true; }, ...props };
    listeners.get(type)?.(e);
    return e;
  }
  function tap(props) {
    event('pointerdown', props);
    event('pointerup', props);
    return event('touchend');
  }
  return { root, field, doc, children, window, event, tap,
    createField() { field = new Element('textarea', root); return field; },
    advance(ms) {
      clock += ms;
      for (const [id, timer] of timers) if (timer.at <= clock) { timers.delete(id); timer.fn(); }
    },
  };
}

test('primo tocco: focus nel gesto senza blur anche se Compose ha gia preso il focus', () => {
  const s = setup();
  s.field.focus();
  assert.equal(s.tap().prevented, true);
  assert.equal(s.field.blurCalls, 0);
  assert.equal(s.field.focusCalls, 2);
  s.field.focus(); // updateState asincrono di Compose
  assert.equal(s.field.focusCalls, 2);
});

test('tocco con tastiera aperta: impedisce il focus sul canvas senza rifocalizzare', () => {
  const s = setup();
  s.field.focus();
  s.window.visualViewport.height = 450;
  assert.equal(s.tap().prevented, true);
  assert.equal(s.field.focusCalls, 1);
  assert.equal(s.field.blurCalls, 0);
});

test('riapre dal gesto una tastiera chiusa con input ancora attivo', () => {
  const s = setup();
  s.field.focus();
  s.tap();
  s.tap();
  assert.equal(s.field.focusCalls, 3);
  assert.equal(s.field.blurCalls, 0);
});

test('appoggio: un vecchio timeout non interrompe il nuovo tentativo', () => {
  const s = setup({ input: false });
  s.tap();
  s.advance(4000);
  s.tap();
  s.advance(1000);
  const decoy = s.children[0];
  assert.equal(decoy.blurCalls, 0);
  const field = s.createField();
  field.focus();
  s.advance(5000);
  assert.equal(decoy.blurCalls, 0);
  assert.equal(s.root.onFocus, null);
  assert.equal(s.root.activeElement, field);
});

test('tocchi esterni, scroll, mouse e gesti cancellati non aprono la tastiera', () => {
  const s = setup();
  assert.equal(s.tap({ clientX: 300 }).prevented, false);
  assert.equal(s.tap({ pointerType: 'mouse' }).prevented, false);
  s.event('pointerdown');
  s.event('pointerup', { clientY: 145 });
  assert.equal(s.event('touchend').prevented, false);
  s.event('pointerdown');
  s.event('pointercancel');
  s.event('pointerup');
  assert.equal(s.event('touchend').prevented, false);
  assert.equal(s.field.focusCalls, 0);
});

test('altri sistemi e kbdoff mantengono il comportamento nativo', () => {
  for (const options of [{ ios: false }, { search: '?kbdoff=1' }]) {
    const s = setup(options);
    assert.equal(s.tap().prevented, false);
    s.field.focus();
    s.field.focus();
    assert.equal(s.field.focusCalls, 2);
  }
});
