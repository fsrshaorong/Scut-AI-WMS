/**
 * 器具包装参数 API。
 *
 * @author Focus
 * @date 2026-06-15
 */
import request from './request'

export function getAppliances(params) {
  return request.get('/appliances', { params })
}

export function getApplianceById(id) {
  return request.get(`/appliances/${id}`)
}

export function createAppliance(data) {
  return request.post('/appliances', data)
}

export function updateAppliance(id, data) {
  return request.put(`/appliances/${id}`, data)
}

export function deleteAppliance(id) {
  return request.delete(`/appliances/${id}`)
}

export function lookupAppliance(materialCode, supplierCode) {
  return request.get('/appliances/lookup', { params: { materialCode, supplierCode } })
}
