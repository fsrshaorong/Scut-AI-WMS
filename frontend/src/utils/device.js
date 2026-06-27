/**
 * 设备检测工具，用于区分 PDA/移动端与桌面端。
 *
 * @author Focus
 * @date 2026-06-24
 */

/**
 * 检测当前是否为 PDA / 移动设备。
 * 仅以 User-Agent 判定，不使用屏幕宽度（桌面端窄窗会误判）。
 *
 * @returns {boolean} true 表示 PDA/移动设备
 */
export function isMobile() {
  // 仅以 User-Agent 判定设备类型
  // 屏幕宽度不作为判定条件，避免桌面端窄窗（开发者工具/小窗模式）误判为移动端
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
