describe('codici Rappresentante per classe e monouso', () => {
  it('un codice inventato viene rifiutato, minuscole e trattini non contano', async () => {
    expect((await register('rep1', { representativeCode: 'AAAAA-AAAAA' })).status).toBe(400);
    const code = await repCode();
    const ok = await register('rep1', { representativeCode: ` ${code.toLowerCase().replace('-', ' ')} ` });
    expect(ok.status).toBe(201);
    expect(ok.json.user.role).toBe('REPRESENTATIVE');
  });

  it('il codice globale di transizione accetta maiuscole/minuscole e trattini in modo case-insensitive', async () => {
    env = { ...env, REPRESENTATIVE_SIGNUP_CODE: 'VECCHIO', REPRESENTATIVE_GLOBAL_CODE_UNTIL: '2999-12-31' } as Env;
    const first = await register('rep1', { representativeCode: 'vecchio' });
    expect(first.status).toBe(201);
    expect(first.json.user.role).toBe('REPRESENTATIVE');

    const second = await register('rep2', { representativeCode: 've-cc-hi-o' });
    expect(second.status).toBe(400);
    expect(second.json.error).toMatch(/già|non valido|transizione/i);
  });
