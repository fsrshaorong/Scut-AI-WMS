/**
 * 入库单 API。
 *
 * @author Focus
 * @date 2026-06-03
 */
import request from './request'

/**
 * 创建入库单。
 */
export function createInbound(data) {
  return request.post('/inbound/orders', data)
}

/**
 * 分页查询入库单列表。
 */
export function getInboundOrders(params) {
  return request.get('/inbound/orders', { params })
}

/**
 * 查询入库单详情（含明细行）。
 */
export function getInboundDetail(id) {
  return request.get(`/inbound/orders/${id}`)
}

/**
 * 手工确认入库（支持按明细行传入实际到货数量）。
 * @param {number} id 入库单主键 ID
 * @param {Array} details [{ materialCode, actualQty }, ...] 可选，不传则按计划数全量入库
 */
export function confirmInbound(id, details) {
  return request.put(`/inbound/orders/${id}/confirm`, details ? { details } : {})
}
