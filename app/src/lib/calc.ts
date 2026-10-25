/**
 * Safe calculator-style amount evaluator for money fields.
 *
 * Supports `+ - * /` with correct precedence, decimals, spaces, and parentheses.
 * A plain number is valid and returns its value. The result is rounded to two
 * decimals (paise precision) and returned in rupees.
 *
 * No `eval`/`Function` — a tiny tokenizer feeds a recursive-descent parser:
 *   expr   := term (('+' | '-') term)*
 *   term   := factor (('*' | '/') factor)*
 *   factor := number | '(' expr ')'
 *
 * There is no unary minus: a leading operator is invalid, so results are only
 * negative via subtraction (e.g. `5-10`). Callers decide whether a negative or
 * zero result is acceptable; this only reports arithmetic validity.
 */
export type EvalResult = { ok: true; value: number } | { ok: false; reason: string };

type Token = { type: 'num'; value: number } | { type: 'op'; value: '+' | '-' | '*' | '/' } | { type: 'paren'; value: '(' | ')' };

/** Split the expression into tokens, or throw with a reason on an unexpected character. */
function tokenize(expr: string): Token[] {
  const tokens: Token[] = [];
  let i = 0;
  while (i < expr.length) {
    const ch = expr[i];
    if (ch === ' ' || ch === '\t') {
      i++;
      continue;
    }
    if (ch === '+' || ch === '-' || ch === '*' || ch === '/') {
      tokens.push({ type: 'op', value: ch });
      i++;
      continue;
    }
    if (ch === '(' || ch === ')') {
      tokens.push({ type: 'paren', value: ch });
      i++;
      continue;
    }
    if ((ch >= '0' && ch <= '9') || ch === '.') {
      let j = i;
      let dots = 0;
      while (j < expr.length && ((expr[j] >= '0' && expr[j] <= '9') || expr[j] === '.')) {
        if (expr[j] === '.') dots++;
        j++;
      }
      const raw = expr.slice(i, j);
      // Require a well-formed number: at least one digit, at most one dot.
      if (dots > 1 || !/^\d+(\.\d+)?$/.test(raw)) throw new Error('malformed number');
      tokens.push({ type: 'num', value: Number(raw) });
      i = j;
      continue;
    }
    throw new Error('invalid character');
  }
  return tokens;
}

/** Round to two decimals (paise), tolerating binary-float noise from division. */
function round2(n: number): number {
  return Math.round((n + Number.EPSILON * Math.sign(n)) * 100) / 100;
}

export function evalAmount(expr: string): EvalResult {
  if (expr == null || expr.trim() === '') return { ok: false, reason: 'empty' };

  let tokens: Token[];
  try {
    tokens = tokenize(expr);
  } catch (e) {
    return { ok: false, reason: e instanceof Error ? e.message : 'invalid' };
  }
  if (tokens.length === 0) return { ok: false, reason: 'empty' };

  let pos = 0;
  let divByZero = false;

  const peek = () => tokens[pos];

  // factor := number | '(' expr ')'
  const parseFactor = (): number => {
    const t = peek();
    if (!t) throw new Error('unexpected end');
    if (t.type === 'num') {
      pos++;
      return t.value;
    }
    if (t.type === 'paren' && t.value === '(') {
      pos++;
      const v = parseExpr();
      const close = peek();
      if (!close || close.type !== 'paren' || close.value !== ')') throw new Error('expected )');
      pos++;
      return v;
    }
    throw new Error('expected a number');
  };

  // term := factor (('*' | '/') factor)*  (left-associative)
  const parseTermFull = (): number => {
    let v = parseFactor();
    for (;;) {
      const t = peek();
      if (t && t.type === 'op' && (t.value === '*' || t.value === '/')) {
        pos++;
        const rhs = parseFactor();
        if (t.value === '*') {
          v *= rhs;
        } else {
          if (rhs === 0) {
            divByZero = true;
            throw new Error('division by zero');
          }
          v /= rhs;
        }
      } else {
        break;
      }
    }
    return v;
  };

  // expr := term (('+' | '-') term)*
  function parseExpr(): number {
    let v = parseTermFull();
    for (;;) {
      const t = peek();
      if (t && t.type === 'op' && (t.value === '+' || t.value === '-')) {
        pos++;
        const rhs = parseTermFull();
        v = t.value === '+' ? v + rhs : v - rhs;
      } else {
        break;
      }
    }
    return v;
  }

  let value: number;
  try {
    value = parseExpr();
  } catch (e) {
    if (divByZero) return { ok: false, reason: 'division by zero' };
    return { ok: false, reason: e instanceof Error ? e.message : 'malformed expression' };
  }
  // Leftover tokens mean something like "5 5" or "(1)2" — malformed.
  if (pos !== tokens.length) return { ok: false, reason: 'malformed expression' };
  if (!Number.isFinite(value)) return { ok: false, reason: 'not finite' };

  return { ok: true, value: round2(value) };
}
