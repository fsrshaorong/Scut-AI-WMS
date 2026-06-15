/**
 * 出库单 API。
 *
 * @author Focus
 * @date 2026-06-15
 */
import request from './request'

export function createOutbound(data) {
  return request.post('/outbound/orders', data)
}

export function getOutboundOrders(params) {
  return request.get('/outbound/orders', { params })
}

export function confirmOutbound(id) {
  return request.put(`/outbound/orders/${id}/confirm`)
}
