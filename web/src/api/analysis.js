import request from '@/utils/request'

export const trendApi = params => request.get('/analysis/trend', { params })

export const structureApi = params => request.get('/analysis/structure', { params })

export const energyStructureApi = params => request.get('/analysis/energy-structure', { params })

export const structureMonthlyApi = params => request.get('/analysis/structure-monthly', { params })

export const monthlyTrendApi = params => request.get('/analysis/monthly-trend', { params })

export const regionRankingApi = params => request.get('/analysis/region-ranking', { params })

export const mapDataApi = params => request.get('/analysis/map', { params })

export const detailPageApi = params => request.get('/analysis/detail', { params })

export const kpiApi = params => request.get('/analysis/kpi', { params })
