-- =================================================================
-- 全国行政区划 + 省级参数 + 区域电网因子 种子数据
-- 生成：tools/gen_regions.py   数据口径：GB/T 2260（民政部）
-- 省级 GDP/能耗等参数为国家统计局公开统计近似值（演示口径）
-- 电网因子来源：生态环境部 2022 年度全国电网平均排放因子公告
-- 幂等：region_code 唯一键 + INSERT IGNORE，可重复执行
-- =================================================================
USE smart_carbon;

-- 1. 省级行政区（level=1）
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) VALUES ('11', '北京市', 0, 1, 49843, 0);
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) VALUES ('12', '天津市', 0, 1, 18024, 0);
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) VALUES ('13', '河北省', 0, 1, 47526, 0);
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) VALUES ('14', '山西省', 0, 1, 25494, 0);
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) VALUES ('15', '内蒙古自治区', 0, 1, 26314, 0);
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) VALUES ('21', '辽宁省', 0, 1, 32612, 0);
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) VALUES ('22', '吉林省', 0, 1, 14361, 0);
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) VALUES ('23', '黑龙江省', 0, 1, 16480, 0);
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) VALUES ('31', '上海市', 0, 1, 53927, 0);
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) VALUES ('32', '江苏省', 0, 1, 137008, 0);
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) VALUES ('33', '浙江省', 0, 1, 90100, 0);
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) VALUES ('34', '安徽省', 0, 1, 50625, 0);
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) VALUES ('35', '福建省', 0, 1, 57761, 0);
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) VALUES ('36', '江西省', 0, 1, 34202, 0);
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) VALUES ('37', '山东省', 0, 1, 98566, 0);
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) VALUES ('41', '河南省', 0, 1, 63590, 0);
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) VALUES ('42', '湖北省', 0, 1, 60013, 0);
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) VALUES ('43', '湖南省', 0, 1, 53231, 0);
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) VALUES ('44', '广东省', 0, 1, 141633, 0);
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) VALUES ('45', '广西壮族自治区', 0, 1, 28649, 0);
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) VALUES ('46', '海南省', 0, 1, 7936, 0);
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) VALUES ('50', '重庆市', 0, 1, 32193, 0);
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) VALUES ('51', '四川省', 0, 1, 64697, 0);
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) VALUES ('52', '贵州省', 0, 1, 22667, 0);
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) VALUES ('53', '云南省', 0, 1, 31534, 0);
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) VALUES ('54', '西藏自治区', 0, 1, 2765, 0);
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) VALUES ('61', '陕西省', 0, 1, 35539, 0);
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) VALUES ('62', '甘肃省', 0, 1, 13003, 0);
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) VALUES ('63', '青海省', 0, 1, 3950, 0);
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) VALUES ('64', '宁夏回族自治区', 0, 1, 5502, 0);
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) VALUES ('65', '新疆维吾尔自治区', 0, 1, 20534, 0);

-- 2. 市级行政区（level=2，GDP 按省 GDP 权重分配，万亿城市按公开排名加权）
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '110101', '东城区', r.id, 2, 3296.41, 0 FROM dim_region r WHERE r.region_code = '11';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '110102', '西城区', r.id, 2, 3387.98, 0 FROM dim_region r WHERE r.region_code = '11';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '110105', '朝阳区', r.id, 2, 3326.94, 0 FROM dim_region r WHERE r.region_code = '11';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '110106', '丰台区', r.id, 2, 3113.28, 0 FROM dim_region r WHERE r.region_code = '11';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '110107', '石景山区', r.id, 2, 3021.71, 0 FROM dim_region r WHERE r.region_code = '11';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '110108', '海淀区', r.id, 2, 3204.85, 0 FROM dim_region r WHERE r.region_code = '11';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '110109', '门头沟区', r.id, 2, 2685.97, 0 FROM dim_region r WHERE r.region_code = '11';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '110111', '房山区', r.id, 2, 3296.41, 0 FROM dim_region r WHERE r.region_code = '11';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '110112', '通州区', r.id, 2, 3113.28, 0 FROM dim_region r WHERE r.region_code = '11';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '110113', '顺义区', r.id, 2, 2869.1, 0 FROM dim_region r WHERE r.region_code = '11';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '110114', '昌平区', r.id, 2, 2960.67, 0 FROM dim_region r WHERE r.region_code = '11';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '110115', '大兴区', r.id, 2, 2808.06, 0 FROM dim_region r WHERE r.region_code = '11';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '110116', '怀柔区', r.id, 2, 3113.28, 0 FROM dim_region r WHERE r.region_code = '11';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '110117', '平谷区', r.id, 2, 3326.94, 0 FROM dim_region r WHERE r.region_code = '11';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '110118', '密云区', r.id, 2, 3357.46, 0 FROM dim_region r WHERE r.region_code = '11';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '110119', '延庆区', r.id, 2, 2960.67, 0 FROM dim_region r WHERE r.region_code = '11';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '120101', '和平区', r.id, 2, 1040.3, 0 FROM dim_region r WHERE r.region_code = '12';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '120102', '河东区', r.id, 2, 1215.63, 0 FROM dim_region r WHERE r.region_code = '12';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '120103', '河西区', r.id, 2, 1075.36, 0 FROM dim_region r WHERE r.region_code = '12';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '120104', '南开区', r.id, 2, 1028.61, 0 FROM dim_region r WHERE r.region_code = '12';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '120105', '河北区', r.id, 2, 1122.12, 0 FROM dim_region r WHERE r.region_code = '12';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '120106', '红桥区', r.id, 2, 1122.12, 0 FROM dim_region r WHERE r.region_code = '12';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '120110', '东丽区', r.id, 2, 1145.49, 0 FROM dim_region r WHERE r.region_code = '12';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '120111', '西青区', r.id, 2, 1133.81, 0 FROM dim_region r WHERE r.region_code = '12';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '120112', '津南区', r.id, 2, 1040.3, 0 FROM dim_region r WHERE r.region_code = '12';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '120113', '北辰区', r.id, 2, 1192.25, 0 FROM dim_region r WHERE r.region_code = '12';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '120114', '武清区', r.id, 2, 1203.94, 0 FROM dim_region r WHERE r.region_code = '12';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '120115', '宝坻区', r.id, 2, 1063.67, 0 FROM dim_region r WHERE r.region_code = '12';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '120116', '滨海新区', r.id, 2, 1145.49, 0 FROM dim_region r WHERE r.region_code = '12';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '120117', '宁河区', r.id, 2, 1122.12, 0 FROM dim_region r WHERE r.region_code = '12';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '120118', '静海区', r.id, 2, 1087.05, 0 FROM dim_region r WHERE r.region_code = '12';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '120119', '蓟州区', r.id, 2, 1285.76, 0 FROM dim_region r WHERE r.region_code = '12';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '1301', '石家庄市', r.id, 2, 5807.4, 0 FROM dim_region r WHERE r.region_code = '13';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '1302', '唐山市', r.id, 2, 6817.38, 0 FROM dim_region r WHERE r.region_code = '13';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '1303', '秦皇岛市', r.id, 2, 3219.32, 0 FROM dim_region r WHERE r.region_code = '13';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '1304', '邯郸市', r.id, 2, 4746.92, 0 FROM dim_region r WHERE r.region_code = '13';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '1305', '邢台市', r.id, 2, 4052.56, 0 FROM dim_region r WHERE r.region_code = '13';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '1306', '保定市', r.id, 2, 4797.42, 0 FROM dim_region r WHERE r.region_code = '13';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '1307', '张家口市', r.id, 2, 3124.63, 0 FROM dim_region r WHERE r.region_code = '13';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '1308', '承德市', r.id, 2, 2348.21, 0 FROM dim_region r WHERE r.region_code = '13';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '1309', '沧州市', r.id, 2, 4494.42, 0 FROM dim_region r WHERE r.region_code = '13';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '1310', '廊坊市', r.id, 2, 5150.91, 0 FROM dim_region r WHERE r.region_code = '13';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '1311', '衡水市', r.id, 2, 2966.82, 0 FROM dim_region r WHERE r.region_code = '13';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '1401', '太原市', r.id, 2, 4559.48, 0 FROM dim_region r WHERE r.region_code = '14';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '1402', '大同市', r.id, 2, 1954.06, 0 FROM dim_region r WHERE r.region_code = '14';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '1403', '阳泉市', r.id, 2, 1893.27, 0 FROM dim_region r WHERE r.region_code = '14';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '1404', '长治市', r.id, 2, 2323.16, 0 FROM dim_region r WHERE r.region_code = '14';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '1405', '晋城市', r.id, 2, 2127.76, 0 FROM dim_region r WHERE r.region_code = '14';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '1406', '朔州市', r.id, 2, 1237.57, 0 FROM dim_region r WHERE r.region_code = '14';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '1407', '晋中市', r.id, 2, 2236.32, 0 FROM dim_region r WHERE r.region_code = '14';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '1408', '运城市', r.id, 2, 2813.85, 0 FROM dim_region r WHERE r.region_code = '14';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '1409', '忻州市', r.id, 2, 1597.99, 0 FROM dim_region r WHERE r.region_code = '14';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '1410', '临汾市', r.id, 2, 2839.9, 0 FROM dim_region r WHERE r.region_code = '14';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '1411', '吕梁市', r.id, 2, 1910.64, 0 FROM dim_region r WHERE r.region_code = '14';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '1501', '呼和浩特市', r.id, 2, 3628.55, 0 FROM dim_region r WHERE r.region_code = '15';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '1502', '包头市', r.id, 2, 4610.54, 0 FROM dim_region r WHERE r.region_code = '15';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '1503', '乌海市', r.id, 2, 1161.62, 0 FROM dim_region r WHERE r.region_code = '15';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '1504', '赤峰市', r.id, 2, 2514.84, 0 FROM dim_region r WHERE r.region_code = '15';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '1505', '通辽市', r.id, 2, 2175.54, 0 FROM dim_region r WHERE r.region_code = '15';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '1506', '鄂尔多斯市', r.id, 2, 5345.03, 0 FROM dim_region r WHERE r.region_code = '15';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '1507', '呼伦贝尔市', r.id, 2, 1995.9, 0 FROM dim_region r WHERE r.region_code = '15';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '1508', '巴彦淖尔市', r.id, 2, 1209.52, 0 FROM dim_region r WHERE r.region_code = '15';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '1509', '乌兰察布市', r.id, 2, 1185.57, 0 FROM dim_region r WHERE r.region_code = '15';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '1522', '兴安盟', r.id, 2, 782.39, 0 FROM dim_region r WHERE r.region_code = '15';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '1525', '锡林郭勒盟', r.id, 2, 1269.4, 0 FROM dim_region r WHERE r.region_code = '15';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '1529', '阿拉善盟', r.id, 2, 435.11, 0 FROM dim_region r WHERE r.region_code = '15';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '2101', '沈阳市', r.id, 2, 5230.33, 0 FROM dim_region r WHERE r.region_code = '21';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '2102', '大连市', r.id, 2, 5626.92, 0 FROM dim_region r WHERE r.region_code = '21';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '2103', '鞍山市', r.id, 2, 3189.93, 0 FROM dim_region r WHERE r.region_code = '21';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '2104', '抚顺市', r.id, 2, 1655.31, 0 FROM dim_region r WHERE r.region_code = '21';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '2105', '本溪市', r.id, 2, 1672.56, 0 FROM dim_region r WHERE r.region_code = '21';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '2106', '丹东市', r.id, 2, 1586.34, 0 FROM dim_region r WHERE r.region_code = '21';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '2107', '锦州市', r.id, 2, 2115.12, 0 FROM dim_region r WHERE r.region_code = '21';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '2108', '营口市', r.id, 2, 2092.13, 0 FROM dim_region r WHERE r.region_code = '21';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '2109', '阜新市', r.id, 2, 1080.55, 0 FROM dim_region r WHERE r.region_code = '21';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '2110', '辽阳市', r.id, 2, 1707.04, 0 FROM dim_region r WHERE r.region_code = '21';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '2111', '盘锦市', r.id, 2, 2092.13, 0 FROM dim_region r WHERE r.region_code = '21';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '2112', '铁岭市', r.id, 2, 1011.58, 0 FROM dim_region r WHERE r.region_code = '21';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '2113', '朝阳市', r.id, 2, 1672.56, 0 FROM dim_region r WHERE r.region_code = '21';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '2114', '葫芦岛市', r.id, 2, 1879.47, 0 FROM dim_region r WHERE r.region_code = '21';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '2201', '长春市', r.id, 2, 4179.83, 0 FROM dim_region r WHERE r.region_code = '22';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '2202', '吉林市', r.id, 2, 2419.9, 0 FROM dim_region r WHERE r.region_code = '22';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '2203', '四平市', r.id, 2, 1214.35, 0 FROM dim_region r WHERE r.region_code = '22';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '2204', '辽源市', r.id, 2, 827.17, 0 FROM dim_region r WHERE r.region_code = '22';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '2205', '通化市', r.id, 2, 1372.74, 0 FROM dim_region r WHERE r.region_code = '22';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '2206', '白山市', r.id, 2, 950.36, 0 FROM dim_region r WHERE r.region_code = '22';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '2207', '松原市', r.id, 2, 1333.14, 0 FROM dim_region r WHERE r.region_code = '22';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '2208', '白城市', r.id, 2, 783.17, 0 FROM dim_region r WHERE r.region_code = '22';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '2224', '延边朝鲜族自治州', r.id, 2, 1280.35, 0 FROM dim_region r WHERE r.region_code = '22';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '2301', '哈尔滨市', r.id, 2, 3409.35, 0 FROM dim_region r WHERE r.region_code = '23';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '2302', '齐齐哈尔市', r.id, 2, 1651.95, 0 FROM dim_region r WHERE r.region_code = '23';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '2303', '鸡西市', r.id, 2, 896.27, 0 FROM dim_region r WHERE r.region_code = '23';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '2304', '鹤岗市', r.id, 2, 478.89, 0 FROM dim_region r WHERE r.region_code = '23';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '2305', '双鸭山市', r.id, 2, 896.27, 0 FROM dim_region r WHERE r.region_code = '23';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '2306', '大庆市', r.id, 2, 2609.74, 0 FROM dim_region r WHERE r.region_code = '23';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '2307', '伊春市', r.id, 2, 905.06, 0 FROM dim_region r WHERE r.region_code = '23';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '2308', '佳木斯市', r.id, 2, 1463.03, 0 FROM dim_region r WHERE r.region_code = '23';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '2309', '七台河市', r.id, 2, 421.78, 0 FROM dim_region r WHERE r.region_code = '23';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '2310', '牡丹江市', r.id, 2, 1159.88, 0 FROM dim_region r WHERE r.region_code = '23';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '2311', '黑河市', r.id, 2, 825.98, 0 FROM dim_region r WHERE r.region_code = '23';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '2312', '绥化市', r.id, 2, 1304.87, 0 FROM dim_region r WHERE r.region_code = '23';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '2327', '大兴安岭地区', r.id, 2, 456.92, 0 FROM dim_region r WHERE r.region_code = '23';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '310101', '黄浦区', r.id, 2, 3200.21, 0 FROM dim_region r WHERE r.region_code = '31';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '310104', '徐汇区', r.id, 2, 3608.75, 0 FROM dim_region r WHERE r.region_code = '31';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '310105', '长宁区', r.id, 2, 3336.39, 0 FROM dim_region r WHERE r.region_code = '31';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '310106', '静安区', r.id, 2, 3676.84, 0 FROM dim_region r WHERE r.region_code = '31';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '310107', '普陀区', r.id, 2, 3676.84, 0 FROM dim_region r WHERE r.region_code = '31';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '310109', '虹口区', r.id, 2, 3166.17, 0 FROM dim_region r WHERE r.region_code = '31';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '310110', '杨浦区', r.id, 2, 3200.21, 0 FROM dim_region r WHERE r.region_code = '31';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '310112', '闵行区', r.id, 2, 3438.53, 0 FROM dim_region r WHERE r.region_code = '31';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '310113', '宝山区', r.id, 2, 2995.94, 0 FROM dim_region r WHERE r.region_code = '31';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '310114', '嘉定区', r.id, 2, 3234.26, 0 FROM dim_region r WHERE r.region_code = '31';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '310115', '浦东新区', r.id, 2, 3506.62, 0 FROM dim_region r WHERE r.region_code = '31';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '310116', '金山区', r.id, 2, 3302.35, 0 FROM dim_region r WHERE r.region_code = '31';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '310117', '松江区', r.id, 2, 3268.3, 0 FROM dim_region r WHERE r.region_code = '31';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '310118', '青浦区', r.id, 2, 3642.8, 0 FROM dim_region r WHERE r.region_code = '31';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '310120', '奉贤区', r.id, 2, 3302.35, 0 FROM dim_region r WHERE r.region_code = '31';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '310151', '崇明区', r.id, 2, 3370.44, 0 FROM dim_region r WHERE r.region_code = '31';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '3201', '南京市', r.id, 2, 18643.13, 0 FROM dim_region r WHERE r.region_code = '32';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '3202', '无锡市', r.id, 2, 14235.42, 0 FROM dim_region r WHERE r.region_code = '32';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '3203', '徐州市', r.id, 2, 10439.31, 0 FROM dim_region r WHERE r.region_code = '32';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '3204', '常州市', r.id, 2, 11251.25, 0 FROM dim_region r WHERE r.region_code = '32';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '3205', '苏州市', r.id, 2, 22565.78, 0 FROM dim_region r WHERE r.region_code = '32';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '3206', '南通市', r.id, 2, 12021.02, 0 FROM dim_region r WHERE r.region_code = '32';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '3207', '连云港市', r.id, 2, 6137.05, 0 FROM dim_region r WHERE r.region_code = '32';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '3208', '淮安市', r.id, 2, 5567.63, 0 FROM dim_region r WHERE r.region_code = '32';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '3209', '盐城市', r.id, 2, 8941.95, 0 FROM dim_region r WHERE r.region_code = '32';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '3210', '扬州市', r.id, 2, 8435.8, 0 FROM dim_region r WHERE r.region_code = '32';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '3211', '镇江市', r.id, 2, 5883.97, 0 FROM dim_region r WHERE r.region_code = '32';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '3212', '泰州市', r.id, 2, 7455.14, 0 FROM dim_region r WHERE r.region_code = '32';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '3213', '宿迁市', r.id, 2, 5430.55, 0 FROM dim_region r WHERE r.region_code = '32';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '3301', '杭州市', r.id, 2, 18168.65, 0 FROM dim_region r WHERE r.region_code = '33';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '3302', '宁波市', r.id, 2, 14589.98, 0 FROM dim_region r WHERE r.region_code = '33';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '3303', '温州市', r.id, 2, 11341.64, 0 FROM dim_region r WHERE r.region_code = '33';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '3304', '嘉兴市', r.id, 2, 9166.91, 0 FROM dim_region r WHERE r.region_code = '33';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '3305', '湖州市', r.id, 2, 5046.85, 0 FROM dim_region r WHERE r.region_code = '33';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '3306', '绍兴市', r.id, 2, 7561.1, 0 FROM dim_region r WHERE r.region_code = '33';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '3307', '金华市', r.id, 2, 7487.69, 0 FROM dim_region r WHERE r.region_code = '33';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '3308', '衢州市', r.id, 2, 3817.25, 0 FROM dim_region r WHERE r.region_code = '33';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '3309', '舟山市', r.id, 2, 2615.18, 0 FROM dim_region r WHERE r.region_code = '33';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '3310', '台州市', r.id, 2, 7854.73, 0 FROM dim_region r WHERE r.region_code = '33';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '3311', '丽水市', r.id, 2, 2450.02, 0 FROM dim_region r WHERE r.region_code = '33';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '3401', '合肥市', r.id, 2, 7283.46, 0 FROM dim_region r WHERE r.region_code = '34';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '3402', '芜湖市', r.id, 2, 4725.58, 0 FROM dim_region r WHERE r.region_code = '34';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '3403', '蚌埠市', r.id, 2, 2848.97, 0 FROM dim_region r WHERE r.region_code = '34';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '3404', '淮南市', r.id, 2, 2749.88, 0 FROM dim_region r WHERE r.region_code = '34';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '3405', '马鞍山市', r.id, 2, 2601.24, 0 FROM dim_region r WHERE r.region_code = '34';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '3406', '淮北市', r.id, 2, 1727.96, 0 FROM dim_region r WHERE r.region_code = '34';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '3407', '铜陵市', r.id, 2, 1932.35, 0 FROM dim_region r WHERE r.region_code = '34';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '3408', '安庆市', r.id, 2, 3716.05, 0 FROM dim_region r WHERE r.region_code = '34';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '3410', '黄山市', r.id, 2, 1932.35, 0 FROM dim_region r WHERE r.region_code = '34';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '3411', '滁州市', r.id, 2, 3493.09, 0 FROM dim_region r WHERE r.region_code = '34';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '3412', '阜阳市', r.id, 2, 4465.45, 0 FROM dim_region r WHERE r.region_code = '34';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '3413', '宿州市', r.id, 2, 3065.74, 0 FROM dim_region r WHERE r.region_code = '34';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '3415', '六安市', r.id, 2, 3003.81, 0 FROM dim_region r WHERE r.region_code = '34';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '3416', '亳州市', r.id, 2, 2601.24, 0 FROM dim_region r WHERE r.region_code = '34';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '3417', '池州市', r.id, 2, 1876.61, 0 FROM dim_region r WHERE r.region_code = '34';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '3418', '宣城市', r.id, 2, 2601.24, 0 FROM dim_region r WHERE r.region_code = '34';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '3501', '福州市', r.id, 2, 11687.32, 0 FROM dim_region r WHERE r.region_code = '35';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '3502', '厦门市', r.id, 2, 7896.84, 0 FROM dim_region r WHERE r.region_code = '35';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '3503', '莆田市', r.id, 2, 4518.75, 0 FROM dim_region r WHERE r.region_code = '35';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '3504', '三明市', r.id, 2, 3474.61, 0 FROM dim_region r WHERE r.region_code = '35';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '3505', '泉州市', r.id, 2, 12319.07, 0 FROM dim_region r WHERE r.region_code = '35';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '3506', '漳州市', r.id, 2, 5957.73, 0 FROM dim_region r WHERE r.region_code = '35';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '3507', '南平市', r.id, 2, 3790.48, 0 FROM dim_region r WHERE r.region_code = '35';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '3508', '龙岩市', r.id, 2, 3992.29, 0 FROM dim_region r WHERE r.region_code = '35';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '3509', '宁德市', r.id, 2, 4123.91, 0 FROM dim_region r WHERE r.region_code = '35';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '3601', '南昌市', r.id, 2, 5509.38, 0 FROM dim_region r WHERE r.region_code = '36';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '3602', '景德镇市', r.id, 2, 1953.33, 0 FROM dim_region r WHERE r.region_code = '36';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '3603', '萍乡市', r.id, 2, 1859.42, 0 FROM dim_region r WHERE r.region_code = '36';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '3604', '九江市', r.id, 2, 3493.45, 0 FROM dim_region r WHERE r.region_code = '36';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '3605', '新余市', r.id, 2, 1821.85, 0 FROM dim_region r WHERE r.region_code = '36';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '3606', '鹰潭市', r.id, 2, 1389.87, 0 FROM dim_region r WHERE r.region_code = '36';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '3607', '赣州市', r.id, 2, 5509.38, 0 FROM dim_region r WHERE r.region_code = '36';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '3608', '吉安市', r.id, 2, 3192.94, 0 FROM dim_region r WHERE r.region_code = '36';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '3609', '宜春市', r.id, 2, 3412.06, 0 FROM dim_region r WHERE r.region_code = '36';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '3610', '抚州市', r.id, 2, 2754.69, 0 FROM dim_region r WHERE r.region_code = '36';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '3611', '上饶市', r.id, 2, 3305.63, 0 FROM dim_region r WHERE r.region_code = '36';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '3701', '济南市', r.id, 2, 12234.7, 0 FROM dim_region r WHERE r.region_code = '37';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '3702', '青岛市', r.id, 2, 12286.98, 0 FROM dim_region r WHERE r.region_code = '37';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '3703', '淄博市', r.id, 2, 4914.79, 0 FROM dim_region r WHERE r.region_code = '37';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '3704', '枣庄市', r.id, 2, 3555.38, 0 FROM dim_region r WHERE r.region_code = '37';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '3705', '东营市', r.id, 2, 3964.95, 0 FROM dim_region r WHERE r.region_code = '37';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '3706', '烟台市', r.id, 2, 9202.17, 0 FROM dim_region r WHERE r.region_code = '37';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '3707', '潍坊市', r.id, 2, 6980.05, 0 FROM dim_region r WHERE r.region_code = '37';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '3708', '济宁市', r.id, 2, 6160.92, 0 FROM dim_region r WHERE r.region_code = '37';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '3709', '泰安市', r.id, 2, 4705.65, 0 FROM dim_region r WHERE r.region_code = '37';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '3710', '威海市', r.id, 2, 4618.51, 0 FROM dim_region r WHERE r.region_code = '37';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '3711', '日照市', r.id, 2, 3137.1, 0 FROM dim_region r WHERE r.region_code = '37';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '3713', '临沂市', r.id, 2, 7598.76, 0 FROM dim_region r WHERE r.region_code = '37';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '3714', '德州市', r.id, 2, 5489.93, 0 FROM dim_region r WHERE r.region_code = '37';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '3715', '聊城市', r.id, 2, 4269.94, 0 FROM dim_region r WHERE r.region_code = '37';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '3716', '滨州市', r.id, 2, 4269.94, 0 FROM dim_region r WHERE r.region_code = '37';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '3717', '菏泽市', r.id, 2, 5176.22, 0 FROM dim_region r WHERE r.region_code = '37';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '4101', '郑州市', r.id, 2, 8321.04, 0 FROM dim_region r WHERE r.region_code = '41';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '4102', '开封市', r.id, 2, 3135.09, 0 FROM dim_region r WHERE r.region_code = '41';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '4103', '洛阳市', r.id, 2, 5172.89, 0 FROM dim_region r WHERE r.region_code = '41';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '4104', '平顶山市', r.id, 2, 2873.83, 0 FROM dim_region r WHERE r.region_code = '41';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '4105', '安阳市', r.id, 2, 3429.0, 0 FROM dim_region r WHERE r.region_code = '41';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '4106', '鹤壁市', r.id, 2, 1802.67, 0 FROM dim_region r WHERE r.region_code = '41';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '4107', '新乡市', r.id, 2, 3840.48, 0 FROM dim_region r WHERE r.region_code = '41';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '4108', '焦作市', r.id, 2, 3298.37, 0 FROM dim_region r WHERE r.region_code = '41';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '4109', '濮阳市', r.id, 2, 2377.44, 0 FROM dim_region r WHERE r.region_code = '41';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '4110', '许昌市', r.id, 2, 3265.71, 0 FROM dim_region r WHERE r.region_code = '41';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '4111', '漯河市', r.id, 2, 1900.65, 0 FROM dim_region r WHERE r.region_code = '41';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '4112', '三门峡市', r.id, 2, 1920.24, 0 FROM dim_region r WHERE r.region_code = '41';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '4113', '南阳市', r.id, 2, 5486.4, 0 FROM dim_region r WHERE r.region_code = '41';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '4114', '商丘市', r.id, 2, 3879.67, 0 FROM dim_region r WHERE r.region_code = '41';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '4115', '信阳市', r.id, 2, 3566.16, 0 FROM dim_region r WHERE r.region_code = '41';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '4116', '周口市', r.id, 2, 3997.24, 0 FROM dim_region r WHERE r.region_code = '41';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '4117', '驻马店市', r.id, 2, 3167.74, 0 FROM dim_region r WHERE r.region_code = '41';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '419001', '济源市', r.id, 2, 2155.37, 0 FROM dim_region r WHERE r.region_code = '41';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '4201', '武汉市', r.id, 2, 14491.57, 0 FROM dim_region r WHERE r.region_code = '42';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '4202', '黄石市', r.id, 2, 2882.92, 0 FROM dim_region r WHERE r.region_code = '42';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '4203', '十堰市', r.id, 2, 2742.97, 0 FROM dim_region r WHERE r.region_code = '42';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '4205', '宜昌市', r.id, 2, 5485.94, 0 FROM dim_region r WHERE r.region_code = '42';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '4206', '襄阳市', r.id, 2, 4800.2, 0 FROM dim_region r WHERE r.region_code = '42';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '4207', '鄂州市', r.id, 2, 2330.13, 0 FROM dim_region r WHERE r.region_code = '42';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '4208', '荆门市', r.id, 2, 2659.0, 0 FROM dim_region r WHERE r.region_code = '42';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '4209', '孝感市', r.id, 2, 3533.68, 0 FROM dim_region r WHERE r.region_code = '42';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '4210', '荆州市', r.id, 2, 4660.25, 0 FROM dim_region r WHERE r.region_code = '42';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '4211', '黄冈市', r.id, 2, 4450.33, 0 FROM dim_region r WHERE r.region_code = '42';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '4212', '咸宁市', r.id, 2, 3078.85, 0 FROM dim_region r WHERE r.region_code = '42';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '4213', '随州市', r.id, 2, 2141.2, 0 FROM dim_region r WHERE r.region_code = '42';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '4228', '恩施土家族苗族自治州', r.id, 2, 1994.25, 0 FROM dim_region r WHERE r.region_code = '42';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '429004', '仙桃市', r.id, 2, 1553.42, 0 FROM dim_region r WHERE r.region_code = '42';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '429005', '潜江市', r.id, 2, 1483.44, 0 FROM dim_region r WHERE r.region_code = '42';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '429006', '天门市', r.id, 2, 1343.5, 0 FROM dim_region r WHERE r.region_code = '42';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '429021', '神农架林区', r.id, 2, 381.36, 0 FROM dim_region r WHERE r.region_code = '42';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '4301', '长沙市', r.id, 2, 9537.89, 0 FROM dim_region r WHERE r.region_code = '43';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '4302', '株洲市', r.id, 2, 3687.33, 0 FROM dim_region r WHERE r.region_code = '43';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '4303', '湘潭市', r.id, 2, 3090.33, 0 FROM dim_region r WHERE r.region_code = '43';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '4304', '衡阳市', r.id, 2, 4670.62, 0 FROM dim_region r WHERE r.region_code = '43';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '4305', '邵阳市', r.id, 2, 3827.8, 0 FROM dim_region r WHERE r.region_code = '43';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '4306', '岳阳市', r.id, 2, 5408.08, 0 FROM dim_region r WHERE r.region_code = '43';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '4307', '常德市', r.id, 2, 3708.4, 0 FROM dim_region r WHERE r.region_code = '43';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '4308', '张家界市', r.id, 2, 2233.47, 0 FROM dim_region r WHERE r.region_code = '43';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '4309', '益阳市', r.id, 2, 2668.92, 0 FROM dim_region r WHERE r.region_code = '43';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '4310', '郴州市', r.id, 2, 3652.21, 0 FROM dim_region r WHERE r.region_code = '43';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '4311', '永州市', r.id, 2, 3301.04, 0 FROM dim_region r WHERE r.region_code = '43';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '4312', '怀化市', r.id, 2, 3006.05, 0 FROM dim_region r WHERE r.region_code = '43';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '4313', '娄底市', r.id, 2, 2584.64, 0 FROM dim_region r WHERE r.region_code = '43';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '4331', '湘西土家族苗族自治州', r.id, 2, 1854.2, 0 FROM dim_region r WHERE r.region_code = '43';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '4401', '广州市', r.id, 2, 24288.52, 0 FROM dim_region r WHERE r.region_code = '44';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '4402', '韶关市', r.id, 2, 3139.0, 0 FROM dim_region r WHERE r.region_code = '44';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '4403', '深圳市', r.id, 2, 23978.5, 0 FROM dim_region r WHERE r.region_code = '44';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '4404', '珠海市', r.id, 2, 4262.84, 0 FROM dim_region r WHERE r.region_code = '44';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '4405', '汕头市', r.id, 2, 4698.82, 0 FROM dim_region r WHERE r.region_code = '44';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '4406', '佛山市', r.id, 2, 11839.08, 0 FROM dim_region r WHERE r.region_code = '44';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '4407', '江门市', r.id, 2, 5231.67, 0 FROM dim_region r WHERE r.region_code = '44';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '4408', '湛江市', r.id, 2, 6394.27, 0 FROM dim_region r WHERE r.region_code = '44';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '4409', '茂名市', r.id, 2, 5377.0, 0 FROM dim_region r WHERE r.region_code = '44';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '4412', '肇庆市', r.id, 2, 4456.61, 0 FROM dim_region r WHERE r.region_code = '44';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '4413', '惠州市', r.id, 2, 6781.8, 0 FROM dim_region r WHERE r.region_code = '44';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '4414', '梅州市', r.id, 2, 3410.28, 0 FROM dim_region r WHERE r.region_code = '44';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '4415', '汕尾市', r.id, 2, 3080.87, 0 FROM dim_region r WHERE r.region_code = '44';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '4416', '河源市', r.id, 2, 2615.84, 0 FROM dim_region r WHERE r.region_code = '44';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '4417', '阳江市', r.id, 2, 2586.77, 0 FROM dim_region r WHERE r.region_code = '44';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '4418', '清远市', r.id, 2, 3875.31, 0 FROM dim_region r WHERE r.region_code = '44';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '4419', '东莞市', r.id, 2, 12904.79, 0 FROM dim_region r WHERE r.region_code = '44';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '4420', '中山市', r.id, 2, 4311.29, 0 FROM dim_region r WHERE r.region_code = '44';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '4451', '潮州市', r.id, 2, 2993.68, 0 FROM dim_region r WHERE r.region_code = '44';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '4452', '揭阳市', r.id, 2, 3604.04, 0 FROM dim_region r WHERE r.region_code = '44';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '4453', '云浮市', r.id, 2, 1802.02, 0 FROM dim_region r WHERE r.region_code = '44';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '4501', '南宁市', r.id, 2, 4299.4, 0 FROM dim_region r WHERE r.region_code = '45';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '4502', '柳州市', r.id, 2, 3057.35, 0 FROM dim_region r WHERE r.region_code = '45';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '4503', '桂林市', r.id, 2, 2866.26, 0 FROM dim_region r WHERE r.region_code = '45';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '4504', '梧州市', r.id, 2, 1674.26, 0 FROM dim_region r WHERE r.region_code = '45';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '4505', '北海市', r.id, 2, 1765.26, 0 FROM dim_region r WHERE r.region_code = '45';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '4506', '防城港市', r.id, 2, 1501.38, 0 FROM dim_region r WHERE r.region_code = '45';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '4507', '钦州市', r.id, 2, 1783.45, 0 FROM dim_region r WHERE r.region_code = '45';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '4508', '贵港市', r.id, 2, 1710.66, 0 FROM dim_region r WHERE r.region_code = '45';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '4509', '玉林市', r.id, 2, 2274.81, 0 FROM dim_region r WHERE r.region_code = '45';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '4510', '百色市', r.id, 2, 2001.84, 0 FROM dim_region r WHERE r.region_code = '45';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '4511', '贺州市', r.id, 2, 1446.78, 0 FROM dim_region r WHERE r.region_code = '45';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '4512', '河池市', r.id, 2, 1783.45, 0 FROM dim_region r WHERE r.region_code = '45';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '4513', '来宾市', r.id, 2, 1242.05, 0 FROM dim_region r WHERE r.region_code = '45';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '4514', '崇左市', r.id, 2, 1242.05, 0 FROM dim_region r WHERE r.region_code = '45';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '4601', '海口市', r.id, 2, 3445.75, 0 FROM dim_region r WHERE r.region_code = '46';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '4602', '三亚市', r.id, 2, 2727.89, 0 FROM dim_region r WHERE r.region_code = '46';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '4603', '三沙市', r.id, 2, 183.06, 0 FROM dim_region r WHERE r.region_code = '46';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '4604', '儋州市', r.id, 2, 1579.3, 0 FROM dim_region r WHERE r.region_code = '46';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '500101', '万州区', r.id, 2, 824.81, 0 FROM dim_region r WHERE r.region_code = '50';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '500102', '涪陵区', r.id, 2, 850.06, 0 FROM dim_region r WHERE r.region_code = '50';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '500103', '渝中区', r.id, 2, 740.65, 0 FROM dim_region r WHERE r.region_code = '50';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '500104', '大渡口区', r.id, 2, 883.73, 0 FROM dim_region r WHERE r.region_code = '50';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '500105', '江北区', r.id, 2, 833.23, 0 FROM dim_region r WHERE r.region_code = '50';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '500106', '沙坪坝区', r.id, 2, 866.9, 0 FROM dim_region r WHERE r.region_code = '50';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '500107', '九龙坡区', r.id, 2, 765.9, 0 FROM dim_region r WHERE r.region_code = '50';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '500108', '南岸区', r.id, 2, 782.73, 0 FROM dim_region r WHERE r.region_code = '50';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '500109', '北碚区', r.id, 2, 858.48, 0 FROM dim_region r WHERE r.region_code = '50';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '500110', '綦江区', r.id, 2, 841.65, 0 FROM dim_region r WHERE r.region_code = '50';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '500111', '大足区', r.id, 2, 866.9, 0 FROM dim_region r WHERE r.region_code = '50';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '500112', '渝北区', r.id, 2, 892.15, 0 FROM dim_region r WHERE r.region_code = '50';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '500113', '巴南区', r.id, 2, 925.81, 0 FROM dim_region r WHERE r.region_code = '50';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '500114', '黔江区', r.id, 2, 883.73, 0 FROM dim_region r WHERE r.region_code = '50';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '500115', '长寿区', r.id, 2, 765.9, 0 FROM dim_region r WHERE r.region_code = '50';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '500116', '江津区', r.id, 2, 908.98, 0 FROM dim_region r WHERE r.region_code = '50';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '500117', '合川区', r.id, 2, 866.9, 0 FROM dim_region r WHERE r.region_code = '50';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '500118', '永川区', r.id, 2, 807.98, 0 FROM dim_region r WHERE r.region_code = '50';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '500119', '南川区', r.id, 2, 892.15, 0 FROM dim_region r WHERE r.region_code = '50';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '500120', '璧山区', r.id, 2, 782.73, 0 FROM dim_region r WHERE r.region_code = '50';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '500151', '铜梁区', r.id, 2, 765.9, 0 FROM dim_region r WHERE r.region_code = '50';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '500152', '潼南区', r.id, 2, 816.4, 0 FROM dim_region r WHERE r.region_code = '50';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '500153', '荣昌区', r.id, 2, 858.48, 0 FROM dim_region r WHERE r.region_code = '50';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '500154', '开州区', r.id, 2, 757.48, 0 FROM dim_region r WHERE r.region_code = '50';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '500155', '梁平区', r.id, 2, 866.9, 0 FROM dim_region r WHERE r.region_code = '50';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '500156', '武隆区', r.id, 2, 900.56, 0 FROM dim_region r WHERE r.region_code = '50';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '500229', '城口县', r.id, 2, 892.15, 0 FROM dim_region r WHERE r.region_code = '50';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '500230', '丰都县', r.id, 2, 757.48, 0 FROM dim_region r WHERE r.region_code = '50';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '500231', '垫江县', r.id, 2, 892.15, 0 FROM dim_region r WHERE r.region_code = '50';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '500233', '忠县', r.id, 2, 900.56, 0 FROM dim_region r WHERE r.region_code = '50';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '500235', '云阳县', r.id, 2, 799.56, 0 FROM dim_region r WHERE r.region_code = '50';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '500236', '奉节县', r.id, 2, 807.98, 0 FROM dim_region r WHERE r.region_code = '50';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '500237', '巫山县', r.id, 2, 866.9, 0 FROM dim_region r WHERE r.region_code = '50';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '500238', '巫溪县', r.id, 2, 925.81, 0 FROM dim_region r WHERE r.region_code = '50';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '500240', '石柱土家族自治县', r.id, 2, 807.98, 0 FROM dim_region r WHERE r.region_code = '50';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '500241', '秀山土家族苗族自治县', r.id, 2, 917.4, 0 FROM dim_region r WHERE r.region_code = '50';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '500242', '酉阳土家族苗族自治县', r.id, 2, 883.73, 0 FROM dim_region r WHERE r.region_code = '50';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '500243', '彭水苗族土家族自治县', r.id, 2, 934.23, 0 FROM dim_region r WHERE r.region_code = '50';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '5101', '成都市', r.id, 2, 12198.5, 0 FROM dim_region r WHERE r.region_code = '51';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '5103', '自贡市', r.id, 2, 2850.6, 0 FROM dim_region r WHERE r.region_code = '51';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '5104', '攀枝花市', r.id, 2, 2118.69, 0 FROM dim_region r WHERE r.region_code = '51';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '5105', '泸州市', r.id, 2, 3113.83, 0 FROM dim_region r WHERE r.region_code = '51';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '5106', '德阳市', r.id, 2, 3434.84, 0 FROM dim_region r WHERE r.region_code = '51';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '5107', '绵阳市', r.id, 2, 4198.85, 0 FROM dim_region r WHERE r.region_code = '51';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '5108', '广元市', r.id, 2, 1694.95, 0 FROM dim_region r WHERE r.region_code = '51';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '5109', '遂宁市', r.id, 2, 2388.34, 0 FROM dim_region r WHERE r.region_code = '51';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '5110', '内江市', r.id, 2, 2285.61, 0 FROM dim_region r WHERE r.region_code = '51';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '5111', '乐山市', r.id, 2, 3499.04, 0 FROM dim_region r WHERE r.region_code = '51';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '5113', '南充市', r.id, 2, 3370.64, 0 FROM dim_region r WHERE r.region_code = '51';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '5114', '眉山市', r.id, 2, 2593.79, 0 FROM dim_region r WHERE r.region_code = '51';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '5115', '宜宾市', r.id, 2, 4160.33, 0 FROM dim_region r WHERE r.region_code = '51';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '5116', '广安市', r.id, 2, 2670.83, 0 FROM dim_region r WHERE r.region_code = '51';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '5117', '达州市', r.id, 2, 3274.33, 0 FROM dim_region r WHERE r.region_code = '51';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '5118', '雅安市', r.id, 2, 1906.82, 0 FROM dim_region r WHERE r.region_code = '51';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '5119', '巴中市', r.id, 2, 1983.86, 0 FROM dim_region r WHERE r.region_code = '51';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '5120', '资阳市', r.id, 2, 2060.9, 0 FROM dim_region r WHERE r.region_code = '51';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '5132', '阿坝藏族羌族自治州', r.id, 2, 1168.49, 0 FROM dim_region r WHERE r.region_code = '51';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '5133', '甘孜藏族自治州', r.id, 2, 1412.46, 0 FROM dim_region r WHERE r.region_code = '51';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '5134', '凉山彝族自治州', r.id, 2, 2311.3, 0 FROM dim_region r WHERE r.region_code = '51';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '5201', '贵阳市', r.id, 2, 4390.71, 0 FROM dim_region r WHERE r.region_code = '52';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '5202', '六盘水市', r.id, 2, 2161.26, 0 FROM dim_region r WHERE r.region_code = '52';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '5203', '遵义市', r.id, 2, 4070.72, 0 FROM dim_region r WHERE r.region_code = '52';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '5204', '安顺市', r.id, 2, 1510.78, 0 FROM dim_region r WHERE r.region_code = '52';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '5205', '毕节市', r.id, 2, 2308.14, 0 FROM dim_region r WHERE r.region_code = '52';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '5206', '铜仁市', r.id, 2, 2203.23, 0 FROM dim_region r WHERE r.region_code = '52';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '5223', '黔西南布依族苗族自治州', r.id, 2, 1888.48, 0 FROM dim_region r WHERE r.region_code = '52';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '5226', '黔东南苗族侗族自治州', r.id, 2, 1846.51, 0 FROM dim_region r WHERE r.region_code = '52';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '5227', '黔南布依族苗族自治州', r.id, 2, 2287.16, 0 FROM dim_region r WHERE r.region_code = '52';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '5301', '昆明市', r.id, 2, 5633.1, 0 FROM dim_region r WHERE r.region_code = '53';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '5303', '曲靖市', r.id, 2, 3550.56, 0 FROM dim_region r WHERE r.region_code = '53';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '5304', '玉溪市', r.id, 2, 2093.92, 0 FROM dim_region r WHERE r.region_code = '53';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '5305', '保山市', r.id, 2, 1621.65, 0 FROM dim_region r WHERE r.region_code = '53';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '5306', '昭通市', r.id, 2, 2389.8, 0 FROM dim_region r WHERE r.region_code = '53';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '5307', '丽江市', r.id, 2, 1138.0, 0 FROM dim_region r WHERE r.region_code = '53';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '5308', '普洱市', r.id, 2, 1877.7, 0 FROM dim_region r WHERE r.region_code = '53';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '5309', '临沧市', r.id, 2, 1035.58, 0 FROM dim_region r WHERE r.region_code = '53';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '5323', '楚雄彝族自治州', r.id, 2, 1792.35, 0 FROM dim_region r WHERE r.region_code = '53';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '5325', '红河哈尼族彝族自治州', r.id, 2, 2759.65, 0 FROM dim_region r WHERE r.region_code = '53';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '5326', '文山壮族苗族自治州', r.id, 2, 1672.86, 0 FROM dim_region r WHERE r.region_code = '53';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '5328', '西双版纳傣族自治州', r.id, 2, 1240.42, 0 FROM dim_region r WHERE r.region_code = '53';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '5329', '大理白族自治州', r.id, 2, 2435.32, 0 FROM dim_region r WHERE r.region_code = '53';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '5331', '德宏傣族景颇族自治州', r.id, 2, 1172.14, 0 FROM dim_region r WHERE r.region_code = '53';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '5333', '怒江傈僳族自治州', r.id, 2, 620.21, 0 FROM dim_region r WHERE r.region_code = '53';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '5334', '迪庆藏族自治州', r.id, 2, 500.72, 0 FROM dim_region r WHERE r.region_code = '53';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '5401', '拉萨市', r.id, 2, 1035.61, 0 FROM dim_region r WHERE r.region_code = '54';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '5402', '日喀则市', r.id, 2, 401.61, 0 FROM dim_region r WHERE r.region_code = '54';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '5403', '昌都市', r.id, 2, 311.36, 0 FROM dim_region r WHERE r.region_code = '54';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '5404', '林芝市', r.id, 2, 328.28, 0 FROM dim_region r WHERE r.region_code = '54';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '5405', '山南市', r.id, 2, 355.35, 0 FROM dim_region r WHERE r.region_code = '54';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '5406', '那曲市', r.id, 2, 214.34, 0 FROM dim_region r WHERE r.region_code = '54';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '5425', '阿里地区', r.id, 2, 118.45, 0 FROM dim_region r WHERE r.region_code = '54';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '6101', '西安市', r.id, 2, 7930.36, 0 FROM dim_region r WHERE r.region_code = '61';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '6102', '铜川市', r.id, 2, 1392.03, 0 FROM dim_region r WHERE r.region_code = '61';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '6103', '宝鸡市', r.id, 2, 3409.78, 0 FROM dim_region r WHERE r.region_code = '61';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '6104', '咸阳市', r.id, 2, 3866.76, 0 FROM dim_region r WHERE r.region_code = '61';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '6105', '渭南市', r.id, 2, 2530.97, 0 FROM dim_region r WHERE r.region_code = '61';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '6106', '延安市', r.id, 2, 3037.16, 0 FROM dim_region r WHERE r.region_code = '61';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '6107', '汉中市', r.id, 2, 2812.19, 0 FROM dim_region r WHERE r.region_code = '61';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '6108', '榆林市', r.id, 2, 7100.77, 0 FROM dim_region r WHERE r.region_code = '61';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '6109', '安康市', r.id, 2, 2193.51, 0 FROM dim_region r WHERE r.region_code = '61';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '6110', '商洛市', r.id, 2, 1265.48, 0 FROM dim_region r WHERE r.region_code = '61';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '6201', '兰州市', r.id, 2, 2314.87, 0 FROM dim_region r WHERE r.region_code = '62';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '6202', '嘉峪关市', r.id, 2, 764.61, 0 FROM dim_region r WHERE r.region_code = '62';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '6203', '金昌市', r.id, 2, 757.59, 0 FROM dim_region r WHERE r.region_code = '62';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '6204', '白银市', r.id, 2, 963.36, 0 FROM dim_region r WHERE r.region_code = '62';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '6205', '天水市', r.id, 2, 925.95, 0 FROM dim_region r WHERE r.region_code = '62';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '6206', '武威市', r.id, 2, 879.18, 0 FROM dim_region r WHERE r.region_code = '62';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '6207', '张掖市', r.id, 2, 750.58, 0 FROM dim_region r WHERE r.region_code = '62';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '6208', '平凉市', r.id, 2, 963.36, 0 FROM dim_region r WHERE r.region_code = '62';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '6209', '酒泉市', r.id, 2, 1038.18, 0 FROM dim_region r WHERE r.region_code = '62';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '6210', '庆阳市', r.id, 2, 1250.96, 0 FROM dim_region r WHERE r.region_code = '62';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '6211', '定西市', r.id, 2, 638.34, 0 FROM dim_region r WHERE r.region_code = '62';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '6212', '陇南市', r.id, 2, 778.64, 0 FROM dim_region r WHERE r.region_code = '62';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '6229', '临夏回族自治州', r.id, 2, 495.71, 0 FROM dim_region r WHERE r.region_code = '62';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '6230', '甘南藏族自治州', r.id, 2, 481.68, 0 FROM dim_region r WHERE r.region_code = '62';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '6301', '西宁市', r.id, 2, 1388.11, 0 FROM dim_region r WHERE r.region_code = '63';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '6302', '海东市', r.id, 2, 673.64, 0 FROM dim_region r WHERE r.region_code = '63';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '6322', '海北藏族自治州', r.id, 2, 353.83, 0 FROM dim_region r WHERE r.region_code = '63';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '6323', '黄南藏族自治州', r.id, 2, 153.1, 0 FROM dim_region r WHERE r.region_code = '63';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '6325', '海南藏族自治州', r.id, 2, 367.44, 0 FROM dim_region r WHERE r.region_code = '63';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '6326', '果洛藏族自治州', r.id, 2, 159.91, 0 FROM dim_region r WHERE r.region_code = '63';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '6327', '玉树藏族自治州', r.id, 2, 187.12, 0 FROM dim_region r WHERE r.region_code = '63';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '6328', '海西蒙古族藏族自治州', r.id, 2, 666.84, 0 FROM dim_region r WHERE r.region_code = '63';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '6401', '银川市', r.id, 2, 2143.53, 0 FROM dim_region r WHERE r.region_code = '64';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '6402', '石嘴山市', r.id, 2, 826.91, 0 FROM dim_region r WHERE r.region_code = '64';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '6403', '吴忠市', r.id, 2, 974.09, 0 FROM dim_region r WHERE r.region_code = '64';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '6404', '固原市', r.id, 2, 786.76, 0 FROM dim_region r WHERE r.region_code = '64';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '6405', '中卫市', r.id, 2, 770.71, 0 FROM dim_region r WHERE r.region_code = '64';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '6501', '乌鲁木齐市', r.id, 2, 3677.73, 0 FROM dim_region r WHERE r.region_code = '65';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '6502', '克拉玛依市', r.id, 2, 2185.01, 0 FROM dim_region r WHERE r.region_code = '65';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '6504', '吐鲁番市', r.id, 2, 778.81, 0 FROM dim_region r WHERE r.region_code = '65';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '6505', '哈密市', r.id, 2, 962.7, 0 FROM dim_region r WHERE r.region_code = '65';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '6523', '昌吉回族自治州', r.id, 2, 1766.75, 0 FROM dim_region r WHERE r.region_code = '65';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '6527', '博尔塔拉蒙古自治州', r.id, 2, 713.91, 0 FROM dim_region r WHERE r.region_code = '65';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '6528', '巴音郭楞蒙古自治州', r.id, 2, 1947.03, 0 FROM dim_region r WHERE r.region_code = '65';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '6529', '阿克苏地区', r.id, 2, 1528.78, 0 FROM dim_region r WHERE r.region_code = '65';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '6530', '克孜勒苏柯尔克孜自治州', r.id, 2, 393.01, 0 FROM dim_region r WHERE r.region_code = '65';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '6531', '喀什地区', r.id, 2, 1384.56, 0 FROM dim_region r WHERE r.region_code = '65';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '6532', '和田地区', r.id, 2, 1146.59, 0 FROM dim_region r WHERE r.region_code = '65';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '6540', '伊犁哈萨克自治州', r.id, 2, 2228.27, 0 FROM dim_region r WHERE r.region_code = '65';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '6542', '塔城地区', r.id, 2, 1070.87, 0 FROM dim_region r WHERE r.region_code = '65';
INSERT IGNORE INTO dim_region (region_code, region_name, parent_id, level, gdp, sort_order) SELECT '6543', '阿勒泰地区', r.id, 2, 749.97, 0 FROM dim_region r WHERE r.region_code = '65';

-- 3. 省级参数表
CREATE TABLE IF NOT EXISTS province_param (
  region_id       INT          NOT NULL COMMENT '省级区域ID',
  gdp             DECIMAL(16,2) NOT NULL COMMENT 'GDP（亿元）',
  secondary_ratio DECIMAL(8,2)  NOT NULL COMMENT '第二产业占GDP比重（%）',
  coal_ratio      DECIMAL(8,2)  NOT NULL COMMENT '煤炭占能源消费比重（%）',
  energy_intensity DECIMAL(8,4) NOT NULL COMMENT '单位GDP能耗（吨标煤/万元）',
  grid_code       VARCHAR(10)   NOT NULL COMMENT '电网区域：HB/DB/HD/HZ/XB/NF',
  heating         TINYINT       NOT NULL DEFAULT 0 COMMENT '是否供暖省份：1是 0否',
  gdp_growth      DECIMAL(6,2)  NOT NULL DEFAULT 5.0 COMMENT 'GDP年均增速（%）',
  PRIMARY KEY (region_id)
) ENGINE=InnoDB COMMENT='省级经济能源特征参数（公开统计近似值）';

INSERT INTO province_param (region_id, gdp, secondary_ratio, coal_ratio, energy_intensity, grid_code, heating, gdp_growth) 
SELECT r.id, 49843, 13.0, 2.0, 0.18, 'HB', 1, 5.0 FROM dim_region r WHERE r.region_code = '11' 
ON DUPLICATE KEY UPDATE gdp = VALUES(gdp), secondary_ratio = VALUES(secondary_ratio), coal_ratio = VALUES(coal_ratio), energy_intensity = VALUES(energy_intensity), grid_code = VALUES(grid_code), heating = VALUES(heating), gdp_growth = VALUES(gdp_growth);
INSERT INTO province_param (region_id, gdp, secondary_ratio, coal_ratio, energy_intensity, grid_code, heating, gdp_growth) 
SELECT r.id, 18024, 36.0, 40.0, 0.35, 'HB', 1, 4.5 FROM dim_region r WHERE r.region_code = '12' 
ON DUPLICATE KEY UPDATE gdp = VALUES(gdp), secondary_ratio = VALUES(secondary_ratio), coal_ratio = VALUES(coal_ratio), energy_intensity = VALUES(energy_intensity), grid_code = VALUES(grid_code), heating = VALUES(heating), gdp_growth = VALUES(gdp_growth);
INSERT INTO province_param (region_id, gdp, secondary_ratio, coal_ratio, energy_intensity, grid_code, heating, gdp_growth) 
SELECT r.id, 47526, 38.0, 55.0, 0.6, 'HB', 1, 5.0 FROM dim_region r WHERE r.region_code = '13' 
ON DUPLICATE KEY UPDATE gdp = VALUES(gdp), secondary_ratio = VALUES(secondary_ratio), coal_ratio = VALUES(coal_ratio), energy_intensity = VALUES(energy_intensity), grid_code = VALUES(grid_code), heating = VALUES(heating), gdp_growth = VALUES(gdp_growth);
INSERT INTO province_param (region_id, gdp, secondary_ratio, coal_ratio, energy_intensity, grid_code, heating, gdp_growth) 
SELECT r.id, 25494, 52.0, 84.0, 1.1, 'HB', 1, 3.5 FROM dim_region r WHERE r.region_code = '14' 
ON DUPLICATE KEY UPDATE gdp = VALUES(gdp), secondary_ratio = VALUES(secondary_ratio), coal_ratio = VALUES(coal_ratio), energy_intensity = VALUES(energy_intensity), grid_code = VALUES(grid_code), heating = VALUES(heating), gdp_growth = VALUES(gdp_growth);
INSERT INTO province_param (region_id, gdp, secondary_ratio, coal_ratio, energy_intensity, grid_code, heating, gdp_growth) 
SELECT r.id, 26314, 46.0, 80.0, 1.3, 'HB', 1, 5.5 FROM dim_region r WHERE r.region_code = '15' 
ON DUPLICATE KEY UPDATE gdp = VALUES(gdp), secondary_ratio = VALUES(secondary_ratio), coal_ratio = VALUES(coal_ratio), energy_intensity = VALUES(energy_intensity), grid_code = VALUES(grid_code), heating = VALUES(heating), gdp_growth = VALUES(gdp_growth);
INSERT INTO province_param (region_id, gdp, secondary_ratio, coal_ratio, energy_intensity, grid_code, heating, gdp_growth) 
SELECT r.id, 32612, 37.0, 55.0, 0.7, 'DB', 1, 4.5 FROM dim_region r WHERE r.region_code = '21' 
ON DUPLICATE KEY UPDATE gdp = VALUES(gdp), secondary_ratio = VALUES(secondary_ratio), coal_ratio = VALUES(coal_ratio), energy_intensity = VALUES(energy_intensity), grid_code = VALUES(grid_code), heating = VALUES(heating), gdp_growth = VALUES(gdp_growth);
INSERT INTO province_param (region_id, gdp, secondary_ratio, coal_ratio, energy_intensity, grid_code, heating, gdp_growth) 
SELECT r.id, 14361, 31.0, 50.0, 0.5, 'DB', 1, 4.0 FROM dim_region r WHERE r.region_code = '22' 
ON DUPLICATE KEY UPDATE gdp = VALUES(gdp), secondary_ratio = VALUES(secondary_ratio), coal_ratio = VALUES(coal_ratio), energy_intensity = VALUES(energy_intensity), grid_code = VALUES(grid_code), heating = VALUES(heating), gdp_growth = VALUES(gdp_growth);
INSERT INTO province_param (region_id, gdp, secondary_ratio, coal_ratio, energy_intensity, grid_code, heating, gdp_growth) 
SELECT r.id, 16480, 27.0, 55.0, 0.55, 'DB', 1, 3.0 FROM dim_region r WHERE r.region_code = '23' 
ON DUPLICATE KEY UPDATE gdp = VALUES(gdp), secondary_ratio = VALUES(secondary_ratio), coal_ratio = VALUES(coal_ratio), energy_intensity = VALUES(energy_intensity), grid_code = VALUES(grid_code), heating = VALUES(heating), gdp_growth = VALUES(gdp_growth);
INSERT INTO province_param (region_id, gdp, secondary_ratio, coal_ratio, energy_intensity, grid_code, heating, gdp_growth) 
SELECT r.id, 53927, 24.0, 15.0, 0.27, 'HD', 0, 4.5 FROM dim_region r WHERE r.region_code = '31' 
ON DUPLICATE KEY UPDATE gdp = VALUES(gdp), secondary_ratio = VALUES(secondary_ratio), coal_ratio = VALUES(coal_ratio), energy_intensity = VALUES(energy_intensity), grid_code = VALUES(grid_code), heating = VALUES(heating), gdp_growth = VALUES(gdp_growth);
INSERT INTO province_param (region_id, gdp, secondary_ratio, coal_ratio, energy_intensity, grid_code, heating, gdp_growth) 
SELECT r.id, 137008, 45.0, 50.0, 0.36, 'HD', 0, 5.5 FROM dim_region r WHERE r.region_code = '32' 
ON DUPLICATE KEY UPDATE gdp = VALUES(gdp), secondary_ratio = VALUES(secondary_ratio), coal_ratio = VALUES(coal_ratio), energy_intensity = VALUES(energy_intensity), grid_code = VALUES(grid_code), heating = VALUES(heating), gdp_growth = VALUES(gdp_growth);
INSERT INTO province_param (region_id, gdp, secondary_ratio, coal_ratio, energy_intensity, grid_code, heating, gdp_growth) 
SELECT r.id, 90100, 41.0, 35.0, 0.35, 'HD', 0, 5.5 FROM dim_region r WHERE r.region_code = '33' 
ON DUPLICATE KEY UPDATE gdp = VALUES(gdp), secondary_ratio = VALUES(secondary_ratio), coal_ratio = VALUES(coal_ratio), energy_intensity = VALUES(energy_intensity), grid_code = VALUES(grid_code), heating = VALUES(heating), gdp_growth = VALUES(gdp_growth);
INSERT INTO province_param (region_id, gdp, secondary_ratio, coal_ratio, energy_intensity, grid_code, heating, gdp_growth) 
SELECT r.id, 50625, 41.0, 55.0, 0.45, 'HD', 0, 5.5 FROM dim_region r WHERE r.region_code = '34' 
ON DUPLICATE KEY UPDATE gdp = VALUES(gdp), secondary_ratio = VALUES(secondary_ratio), coal_ratio = VALUES(coal_ratio), energy_intensity = VALUES(energy_intensity), grid_code = VALUES(grid_code), heating = VALUES(heating), gdp_growth = VALUES(gdp_growth);
INSERT INTO province_param (region_id, gdp, secondary_ratio, coal_ratio, energy_intensity, grid_code, heating, gdp_growth) 
SELECT r.id, 57761, 42.0, 30.0, 0.38, 'HD', 0, 5.5 FROM dim_region r WHERE r.region_code = '35' 
ON DUPLICATE KEY UPDATE gdp = VALUES(gdp), secondary_ratio = VALUES(secondary_ratio), coal_ratio = VALUES(coal_ratio), energy_intensity = VALUES(energy_intensity), grid_code = VALUES(grid_code), heating = VALUES(heating), gdp_growth = VALUES(gdp_growth);
INSERT INTO province_param (region_id, gdp, secondary_ratio, coal_ratio, energy_intensity, grid_code, heating, gdp_growth) 
SELECT r.id, 34202, 43.0, 45.0, 0.45, 'HZ', 0, 5.0 FROM dim_region r WHERE r.region_code = '36' 
ON DUPLICATE KEY UPDATE gdp = VALUES(gdp), secondary_ratio = VALUES(secondary_ratio), coal_ratio = VALUES(coal_ratio), energy_intensity = VALUES(energy_intensity), grid_code = VALUES(grid_code), heating = VALUES(heating), gdp_growth = VALUES(gdp_growth);
INSERT INTO province_param (region_id, gdp, secondary_ratio, coal_ratio, energy_intensity, grid_code, heating, gdp_growth) 
SELECT r.id, 98566, 39.0, 60.0, 0.5, 'HB', 1, 5.0 FROM dim_region r WHERE r.region_code = '37' 
ON DUPLICATE KEY UPDATE gdp = VALUES(gdp), secondary_ratio = VALUES(secondary_ratio), coal_ratio = VALUES(coal_ratio), energy_intensity = VALUES(energy_intensity), grid_code = VALUES(grid_code), heating = VALUES(heating), gdp_growth = VALUES(gdp_growth);
INSERT INTO province_param (region_id, gdp, secondary_ratio, coal_ratio, energy_intensity, grid_code, heating, gdp_growth) 
SELECT r.id, 63590, 38.0, 55.0, 0.45, 'HZ', 1, 5.0 FROM dim_region r WHERE r.region_code = '41' 
ON DUPLICATE KEY UPDATE gdp = VALUES(gdp), secondary_ratio = VALUES(secondary_ratio), coal_ratio = VALUES(coal_ratio), energy_intensity = VALUES(energy_intensity), grid_code = VALUES(grid_code), heating = VALUES(heating), gdp_growth = VALUES(gdp_growth);
INSERT INTO province_param (region_id, gdp, secondary_ratio, coal_ratio, energy_intensity, grid_code, heating, gdp_growth) 
SELECT r.id, 60013, 39.0, 45.0, 0.42, 'HZ', 0, 5.5 FROM dim_region r WHERE r.region_code = '42' 
ON DUPLICATE KEY UPDATE gdp = VALUES(gdp), secondary_ratio = VALUES(secondary_ratio), coal_ratio = VALUES(coal_ratio), energy_intensity = VALUES(energy_intensity), grid_code = VALUES(grid_code), heating = VALUES(heating), gdp_growth = VALUES(gdp_growth);
INSERT INTO province_param (region_id, gdp, secondary_ratio, coal_ratio, energy_intensity, grid_code, heating, gdp_growth) 
SELECT r.id, 53231, 39.0, 45.0, 0.42, 'HZ', 0, 5.0 FROM dim_region r WHERE r.region_code = '43' 
ON DUPLICATE KEY UPDATE gdp = VALUES(gdp), secondary_ratio = VALUES(secondary_ratio), coal_ratio = VALUES(coal_ratio), energy_intensity = VALUES(energy_intensity), grid_code = VALUES(grid_code), heating = VALUES(heating), gdp_growth = VALUES(gdp_growth);
INSERT INTO province_param (region_id, gdp, secondary_ratio, coal_ratio, energy_intensity, grid_code, heating, gdp_growth) 
SELECT r.id, 141633, 39.0, 30.0, 0.3, 'NF', 0, 4.5 FROM dim_region r WHERE r.region_code = '44' 
ON DUPLICATE KEY UPDATE gdp = VALUES(gdp), secondary_ratio = VALUES(secondary_ratio), coal_ratio = VALUES(coal_ratio), energy_intensity = VALUES(energy_intensity), grid_code = VALUES(grid_code), heating = VALUES(heating), gdp_growth = VALUES(gdp_growth);
INSERT INTO province_param (region_id, gdp, secondary_ratio, coal_ratio, energy_intensity, grid_code, heating, gdp_growth) 
SELECT r.id, 28649, 34.0, 45.0, 0.45, 'NF', 0, 4.5 FROM dim_region r WHERE r.region_code = '45' 
ON DUPLICATE KEY UPDATE gdp = VALUES(gdp), secondary_ratio = VALUES(secondary_ratio), coal_ratio = VALUES(coal_ratio), energy_intensity = VALUES(energy_intensity), grid_code = VALUES(grid_code), heating = VALUES(heating), gdp_growth = VALUES(gdp_growth);
INSERT INTO province_param (region_id, gdp, secondary_ratio, coal_ratio, energy_intensity, grid_code, heating, gdp_growth) 
SELECT r.id, 7936, 19.0, 30.0, 0.4, 'NF', 0, 6.0 FROM dim_region r WHERE r.region_code = '46' 
ON DUPLICATE KEY UPDATE gdp = VALUES(gdp), secondary_ratio = VALUES(secondary_ratio), coal_ratio = VALUES(coal_ratio), energy_intensity = VALUES(energy_intensity), grid_code = VALUES(grid_code), heating = VALUES(heating), gdp_growth = VALUES(gdp_growth);
INSERT INTO province_param (region_id, gdp, secondary_ratio, coal_ratio, energy_intensity, grid_code, heating, gdp_growth) 
SELECT r.id, 32193, 40.0, 40.0, 0.4, 'HZ', 0, 5.5 FROM dim_region r WHERE r.region_code = '50' 
ON DUPLICATE KEY UPDATE gdp = VALUES(gdp), secondary_ratio = VALUES(secondary_ratio), coal_ratio = VALUES(coal_ratio), energy_intensity = VALUES(energy_intensity), grid_code = VALUES(grid_code), heating = VALUES(heating), gdp_growth = VALUES(gdp_growth);
INSERT INTO province_param (region_id, gdp, secondary_ratio, coal_ratio, energy_intensity, grid_code, heating, gdp_growth) 
SELECT r.id, 64697, 34.0, 30.0, 0.38, 'HZ', 0, 5.5 FROM dim_region r WHERE r.region_code = '51' 
ON DUPLICATE KEY UPDATE gdp = VALUES(gdp), secondary_ratio = VALUES(secondary_ratio), coal_ratio = VALUES(coal_ratio), energy_intensity = VALUES(energy_intensity), grid_code = VALUES(grid_code), heating = VALUES(heating), gdp_growth = VALUES(gdp_growth);
INSERT INTO province_param (region_id, gdp, secondary_ratio, coal_ratio, energy_intensity, grid_code, heating, gdp_growth) 
SELECT r.id, 22667, 37.0, 55.0, 0.6, 'NF', 0, 5.0 FROM dim_region r WHERE r.region_code = '52' 
ON DUPLICATE KEY UPDATE gdp = VALUES(gdp), secondary_ratio = VALUES(secondary_ratio), coal_ratio = VALUES(coal_ratio), energy_intensity = VALUES(energy_intensity), grid_code = VALUES(grid_code), heating = VALUES(heating), gdp_growth = VALUES(gdp_growth);
INSERT INTO province_param (region_id, gdp, secondary_ratio, coal_ratio, energy_intensity, grid_code, heating, gdp_growth) 
SELECT r.id, 31534, 34.0, 45.0, 0.55, 'NF', 0, 4.5 FROM dim_region r WHERE r.region_code = '53' 
ON DUPLICATE KEY UPDATE gdp = VALUES(gdp), secondary_ratio = VALUES(secondary_ratio), coal_ratio = VALUES(coal_ratio), energy_intensity = VALUES(energy_intensity), grid_code = VALUES(grid_code), heating = VALUES(heating), gdp_growth = VALUES(gdp_growth);
INSERT INTO province_param (region_id, gdp, secondary_ratio, coal_ratio, energy_intensity, grid_code, heating, gdp_growth) 
SELECT r.id, 2765, 32.0, 30.0, 0.45, 'XB', 0, 7.0 FROM dim_region r WHERE r.region_code = '54' 
ON DUPLICATE KEY UPDATE gdp = VALUES(gdp), secondary_ratio = VALUES(secondary_ratio), coal_ratio = VALUES(coal_ratio), energy_intensity = VALUES(energy_intensity), grid_code = VALUES(grid_code), heating = VALUES(heating), gdp_growth = VALUES(gdp_growth);
INSERT INTO province_param (region_id, gdp, secondary_ratio, coal_ratio, energy_intensity, grid_code, heating, gdp_growth) 
SELECT r.id, 35539, 49.0, 68.0, 0.65, 'XB', 1, 5.0 FROM dim_region r WHERE r.region_code = '61' 
ON DUPLICATE KEY UPDATE gdp = VALUES(gdp), secondary_ratio = VALUES(secondary_ratio), coal_ratio = VALUES(coal_ratio), energy_intensity = VALUES(energy_intensity), grid_code = VALUES(grid_code), heating = VALUES(heating), gdp_growth = VALUES(gdp_growth);
INSERT INTO province_param (region_id, gdp, secondary_ratio, coal_ratio, energy_intensity, grid_code, heating, gdp_growth) 
SELECT r.id, 13003, 36.0, 50.0, 0.75, 'XB', 1, 5.0 FROM dim_region r WHERE r.region_code = '62' 
ON DUPLICATE KEY UPDATE gdp = VALUES(gdp), secondary_ratio = VALUES(secondary_ratio), coal_ratio = VALUES(coal_ratio), energy_intensity = VALUES(energy_intensity), grid_code = VALUES(grid_code), heating = VALUES(heating), gdp_growth = VALUES(gdp_growth);
INSERT INTO province_param (region_id, gdp, secondary_ratio, coal_ratio, energy_intensity, grid_code, heating, gdp_growth) 
SELECT r.id, 3950, 43.0, 30.0, 0.8, 'XB', 1, 4.5 FROM dim_region r WHERE r.region_code = '63' 
ON DUPLICATE KEY UPDATE gdp = VALUES(gdp), secondary_ratio = VALUES(secondary_ratio), coal_ratio = VALUES(coal_ratio), energy_intensity = VALUES(energy_intensity), grid_code = VALUES(grid_code), heating = VALUES(heating), gdp_growth = VALUES(gdp_growth);
INSERT INTO province_param (region_id, gdp, secondary_ratio, coal_ratio, energy_intensity, grid_code, heating, gdp_growth) 
SELECT r.id, 5502, 45.0, 80.0, 1.4, 'XB', 1, 5.5 FROM dim_region r WHERE r.region_code = '64' 
ON DUPLICATE KEY UPDATE gdp = VALUES(gdp), secondary_ratio = VALUES(secondary_ratio), coal_ratio = VALUES(coal_ratio), energy_intensity = VALUES(energy_intensity), grid_code = VALUES(grid_code), heating = VALUES(heating), gdp_growth = VALUES(gdp_growth);
INSERT INTO province_param (region_id, gdp, secondary_ratio, coal_ratio, energy_intensity, grid_code, heating, gdp_growth) 
SELECT r.id, 20534, 41.0, 70.0, 1.2, 'XB', 1, 6.0 FROM dim_region r WHERE r.region_code = '65' 
ON DUPLICATE KEY UPDATE gdp = VALUES(gdp), secondary_ratio = VALUES(secondary_ratio), coal_ratio = VALUES(coal_ratio), energy_intensity = VALUES(energy_intensity), grid_code = VALUES(grid_code), heating = VALUES(heating), gdp_growth = VALUES(gdp_growth);

-- 4. 排放因子表增加电网区域维度（幂等）
SET @col_exists = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'factor_emission' AND column_name = 'grid_code');
SET @sql = IF(@col_exists = 0, "ALTER TABLE factor_emission ADD COLUMN grid_code VARCHAR(10) DEFAULT NULL COMMENT '电网区域编码（仅电力因子使用）' AFTER energy_id", 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 替换旧单一全国电力因子为六大区域电网因子（能源品种7=电力）
DELETE FROM factor_emission WHERE energy_id = 7;
INSERT IGNORE INTO factor_emission (energy_id, grid_code, factor_value, oxid_rate, data_source, effective_year, remark) VALUES (7, 'HB', 9.81, 1.0000, '生态环境部2022年度全国电网平均排放因子公告', 2024, '华北区域电网 tCO2/万千瓦时');
INSERT IGNORE INTO factor_emission (energy_id, grid_code, factor_value, oxid_rate, data_source, effective_year, remark) VALUES (7, 'DB', 10.603, 1.0000, '生态环境部2022年度全国电网平均排放因子公告', 2024, '东北区域电网 tCO2/万千瓦时');
INSERT IGNORE INTO factor_emission (energy_id, grid_code, factor_value, oxid_rate, data_source, effective_year, remark) VALUES (7, 'HD', 7.496, 1.0000, '生态环境部2022年度全国电网平均排放因子公告', 2024, '华东区域电网 tCO2/万千瓦时');
INSERT IGNORE INTO factor_emission (energy_id, grid_code, factor_value, oxid_rate, data_source, effective_year, remark) VALUES (7, 'HZ', 6.231, 1.0000, '生态环境部2022年度全国电网平均排放因子公告', 2024, '华中区域电网 tCO2/万千瓦时');
INSERT IGNORE INTO factor_emission (energy_id, grid_code, factor_value, oxid_rate, data_source, effective_year, remark) VALUES (7, 'XB', 7.094, 1.0000, '生态环境部2022年度全国电网平均排放因子公告', 2024, '西北区域电网 tCO2/万千瓦时');
INSERT IGNORE INTO factor_emission (energy_id, grid_code, factor_value, oxid_rate, data_source, effective_year, remark) VALUES (7, 'NF', 5.217, 1.0000, '生态环境部2022年度全国电网平均排放因子公告', 2024, '南方区域电网 tCO2/万千瓦时');

