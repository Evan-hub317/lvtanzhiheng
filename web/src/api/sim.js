import request from '@/utils/request'

export const predictApi = data => request.post('/sim/predict', data, { timeout: 120000 })

export const baseParamApi = regionId => request.get(`/sim/param/${regionId}`)

export const simulateApi = data => request.post('/sim/simulate', data, { timeout: 120000 })

export const saveScenarioApi = data => request.post('/sim/save', data, { timeout: 120000 })

export const scenarioListApi = params => request.get('/sim/list', { params })

export const scenarioDetailApi = id => request.get(`/sim/${id}`)
