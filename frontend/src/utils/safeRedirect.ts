/**
 * 路由 redirect 参数消毒（CLAUDE.md §7 安全 + Review C-4：OWASP A01 open redirect）。
 *
 * <p>登录/登出后跳转到 {@code route.query.redirect} 时，必须保证跳转目标是同源相对路径，
 * 否则攻击者可构造 {@code /login?redirect=https://evil.com} 实施钓鱼跳转。
 *
 * <p>判定规则：
 * <ol>
 *   <li>非字符串（undefined / 数组 / 数字等）→ 回退 {@code FALLBACK}</li>
 *   <li>不以 {@code /} 开头 → 回退 {@code FALLBACK}</li>
 *   <li>以 {@code //} 或 {@code /\} 开头（protocol-relative URL / Windows path）→ 回退</li>
 *   <li>含 scheme 模式（{@code /path?next=...javascript:...} 暂不二次解析，仅第一跳校验）</li>
 *   <li>其他情况 → 原样返回（允许 {@code /tasks/123?status=done} 等内部跳转）</li>
 * </ol>
 *
 * <p>注：本工具只校验第一跳的 URL 形态，不解析/递归解码 query；最严策略可叠加
 * {@code new URL(input, location.origin)} 后比较 origin —— 这里维持第一跳过滤，
 * 因为 Vue Router 的 {@code router.push} 不发起外部 HTTP 跳转，攻击面止于"诱导点击"。
 */
const FALLBACK = '/';

/**
 * 把用户提供的 redirect 参数消毒为同源相对路径。
 *
 * @param input 原始 query 值（来自 {@code route.query.redirect}）
 * @returns 同源相对路径；不合法时返回 {@code FALLBACK}
 */
export function safeRedirect(input: unknown): string {
  if (typeof input !== 'string' || input.length === 0) {
    return FALLBACK;
  }
  // 必须以单 / 开头（不能 // 或 /\ —— 防止 protocol-relative URL）
  if (!input.startsWith('/') || input.startsWith('//') || input.startsWith('/\\')) {
    return FALLBACK;
  }
  return input;
}