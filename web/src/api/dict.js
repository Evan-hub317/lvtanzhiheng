import request from '@/utils/request'

export const regionListApi = () => request.get('/dict/region/list')

export const industryListApi = () => request.get('/dict/industry/list')

export const energyListApi = () => request.get('/dict/energy/list')

export const factorListApi = () => request.get('/dict/factor/list')

export const roleListApi = () => request.get('/dict/role/list')
