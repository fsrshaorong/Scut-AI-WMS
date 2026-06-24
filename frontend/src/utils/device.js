/**
 * 设备检测工具，用于区分 PDA/移动端与桌面端。
 *
 * @author Focus
 * @date 2026-06-24
 */

/**
 * 检测当前是否为 PDA / 移动设备。
 * 综合 User-Agent 和屏幕宽度双重判定。
 *
 * @returns {boolean} true 表示 PDA/移动设备
 */
export function isMobile() {
  // 屏幕宽度 ≤ 768px 视为 PDA/移动端
  if (window.innerWidth <= 768) return true

  // User-Agent 检测
  const ua = navigator.userAgent || ''
  return /Android|webOS|iPhone|iPad|iPod|BlackBerry|IEMobile|Opera Mini/i.test(ua)
}

/**
 * 获取登录后应跳转的目标路由。
 * PDA/移动端跳转 /pda，桌面端跳转 /dashboard。
 *
 * @returns {string} 路由路径
 */
export function getHomeRoute() {
  return isMobile() ? '/pda' : '/dashboard'
}
