/**
 * 需求预测 API。
 * @author Focus
 * @date 2026-06-24
 */
import request from './request'

/** 获取全部需求预测 */
export function getDemandForecasts() {
  return request.get('/demand/forecasts')
}

/** 批量生成全部物料需求预测 */
export function generateAllDemandForecasts() {
  return request.post('/demand/forecasts/generate-all')
}
