/**
 * 供应商 API。
 *
 * @author Focus
 * @date 2026-06-15
 */
import request from './request'

export function getSuppliers(params) {
  return request.get('/suppliers', { params })
}

export function getSupplierById(id) {
  return request.get(`/suppliers/${id}`)
}

export function createSupplier(data) {
  return request.post('/suppliers', data)
}

export function updateSupplier(id, data) {
  return request.put(`/suppliers/${id}`, data)
}

export function deleteSupplier(id) {
  return request.delete(`/suppliers/${id}`)
}
