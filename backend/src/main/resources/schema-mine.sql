-- Mining Management Database Schema
-- Run this SQL to create the mining tables in your PostgreSQL database

CREATE TABLE IF NOT EXISTS mine (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    location VARCHAR(255),
    type VARCHAR(100),
    status VARCHAR(50) DEFAULT 'ACTIVE',
    worker_count INTEGER DEFAULT 0,
    area_hectares DECIMAL(10,2),
    license_number VARCHAR(100),
    operation_start DATE,
    description TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS mine_equipment (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    type VARCHAR(100) NOT NULL,
    mine_id BIGINT REFERENCES mine(id),
    status VARCHAR(50) DEFAULT 'OPERATIONAL',
    utilization_pct DECIMAL(5,2) DEFAULT 0,
    operating_hours BIGINT DEFAULT 0,
    last_maintenance DATE,
    purchase_date DATE,
    model VARCHAR(255),
    serial_number VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS mine_production (
    id BIGSERIAL PRIMARY KEY,
    mine_id BIGINT REFERENCES mine(id),
    production_date DATE NOT NULL,
    material_type VARCHAR(100),
    extracted_tonnes DECIMAL(12,2) DEFAULT 0,
    processed_tonnes DECIMAL(12,2) DEFAULT 0,
    shipped_tonnes DECIMAL(12,2) DEFAULT 0,
    shift VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS mine_safety_incident (
    id BIGSERIAL PRIMARY KEY,
    mine_id BIGINT REFERENCES mine(id),
    incident_date DATE NOT NULL,
    severity VARCHAR(50) NOT NULL,
    description TEXT,
    status VARCHAR(50) DEFAULT 'OPEN',
    reported_by VARCHAR(255),
    corrective_action TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Sample data
INSERT INTO mine (name, location, type, status, worker_count, area_hectares) VALUES
('Mo Da Nang - Quang Nam', 'Quang Nam', 'Da granite', 'ACTIVE', 156, 250.5),
('Mo Pha Le Binh Dinh', 'Binh Dinh', 'Da pha le', 'ACTIVE', 98, 180.3),
('Mo Cat Da Nang', 'Da Nang', 'Cat xay dung', 'ACTIVE', 67, 120.8),
('Mo Da Lit Quang Tri', 'Quang Tri', 'Da lit', 'ACTIVE', 45, 95.2),
('Mo Soi Nghe An', 'Nghe An', 'Soi', 'MAINTENANCE', 32, 78.6),
('Mo Da Hoa Binh', 'Hoa Binh', 'Da granite', 'ACTIVE', 88, 210.4),
('Mo Cat Hai Phong', 'Hai Phong', 'Cat xay dung', 'ACTIVE', 54, 88.1),
('Mo Da Ninh Binh', 'Ninh Binh', 'Da pha le', 'INACTIVE', 0, 65.3),
('Mo Soi Thanh Hoa', 'Thanh Hoa', 'Soi', 'ACTIVE', 41, 72.8),
('Mo Da Nghe An', 'Nghe An', 'Da lit', 'ACTIVE', 62, 145.7),
('Mo Cat Da Lat', 'Lam Dong', 'Cat xay dung', 'MAINTENANCE', 28, 92.4),
('Mo Da Son La', 'Son La', 'Da granite', 'INACTIVE', 0, 110.5),
('Mo Pha Le Kon Tum', 'Kon Tum', 'Da pha le', 'ACTIVE', 35, 82.0);

INSERT INTO mine_equipment (name, type, mine_id, status, utilization_pct, operating_hours, last_maintenance) VALUES
('EX-3200', 'May cuoc', 1, 'OPERATIONAL', 82, 12400, '2026-07-15'),
('CAT-785', 'Xe tai', 1, 'OPERATIONAL', 91, 15600, '2026-08-01'),
('PC-2000', 'May cuoc', 2, 'MAINTENANCE', 0, 9800, '2026-06-20'),
('Komatsu-D61', 'May dap', 3, 'OPERATIONAL', 68, 8200, '2026-07-28'),
('HP-500', 'May nghien', 1, 'OPERATIONAL', 92, 11200, '2026-08-05'),
('Liebherr-LTM', 'Truc bom', 4, 'IDLE', 0, 5400, '2026-05-10'),
('CAT-D10', 'May dap', 2, 'OPERATIONAL', 75, 7600, '2026-07-22'),
('VOLVO-A40G', 'Xe tai', 5, 'OPERATIONAL', 88, 13100, '2026-08-03'),
('XCMG-GR530', 'May dap', 6, 'OPERATIONAL', 71, 6800, '2026-07-20'),
('Hitachi-ZX800', 'May cuoc', 7, 'OPERATIONAL', 85, 9400, '2026-08-08'),
('SCANIA-R500', 'Xe tai', 3, 'OPERATIONAL', 79, 11800, '2026-07-30'),
('Metso-HP400', 'May nghien', 2, 'OPERATIONAL', 88, 8900, '2026-08-01'),
('Sandvik-JD300', 'May cuoc', 6, 'MAINTENANCE', 0, 7200, '2026-06-15'),
('BELAZ-7547', 'Xe tai', 1, 'OPERATIONAL', 93, 14200, '2026-08-05'),
('Atlas-ROC-D65', 'May dap', 4, 'IDLE', 0, 4600, '2026-04-20'),
('CASE-988K', 'May cuoc', 8, 'OPERATIONAL', 62, 5800, '2026-07-18'),
('Volvo-A30G', 'Xe tai', 9, 'OPERATIONAL', 82, 10500, '2026-08-02'),
('Kobelco-SK460', 'May cuoc', 10, 'OPERATIONAL', 76, 8100, '2026-07-25'),
('Liebherr-T264', 'Xe tai', 6, 'MAINTENANCE', 0, 12800, '2026-07-10'),
('Terex-TR100', 'Xe tai', 1, 'OPERATIONAL', 87, 16200, '2026-08-06');

INSERT INTO mine_production (mine_id, production_date, material_type, extracted_tonnes, processed_tonnes, shipped_tonnes, shift) VALUES
(1, '2026-08-17', 'Da granite', 520, 480, 450, 'Ca sang'),
(2, '2026-08-17', 'Da pha le', 380, 350, 320, 'Ca sang'),
(3, '2026-08-17', 'Cat xay dung', 290, 270, 260, 'Ca sang'),
(1, '2026-08-16', 'Da granite', 510, 470, 440, 'Ca chieu'),
(4, '2026-08-16', 'Da lit', 220, 200, 180, 'Ca sang'),
(5, '2026-08-16', 'Soi', 180, 170, 150, 'Ca dem'),
(6, '2026-08-15', 'Da granite', 450, 420, 400, 'Ca sang'),
(7, '2026-08-15', 'Cat xay dung', 310, 290, 270, 'Ca chieu'),
(2, '2026-08-15', 'Da pha le', 360, 330, 300, 'Ca sang'),
(9, '2026-08-14', 'Soi', 200, 185, 170, 'Ca sang'),
(10, '2026-08-14', 'Da lit', 280, 260, 240, 'Ca chieu'),
(1, '2026-08-14', 'Da granite', 530, 490, 460, 'Ca dem'),
(3, '2026-08-13', 'Cat xay dung', 285, 265, 250, 'Ca sang'),
(6, '2026-08-13', 'Da granite', 440, 410, 390, 'Ca chieu'),
(8, '2026-08-13', 'Da pha le', 320, 295, 275, 'Ca sang');

INSERT INTO mine_safety_incident (mine_id, incident_date, severity, description, status, reported_by) VALUES
(1, '2026-08-10', 'MAJOR', 'Roi da tu do cao, khong co thuong vong', 'RESOLVED', 'Nguyen Van A'),
(2, '2026-08-05', 'MINOR', 'Hong he thong bao ho lam viec', 'RESOLVED', 'Tran Thi B'),
(3, '2026-07-28', 'CRITICAL', 'Ha sap o khu vuc khai thac A3', 'IN_PROGRESS', 'Le Van C'),
(4, '2026-07-20', 'MINOR', 'Tieeng on vuot qua cho phep', 'RESOLVED', 'Pham Thi D'),
(1, '2026-07-15', 'MAJOR', 'May cuoc bi hong he thong hydraulics', 'RESOLVED', 'Hoang Van E'),
(6, '2026-07-10', 'MINOR', 'Roi dien o khu vuc 5', 'RESOLVED', 'Vo Thi F'),
(5, '2026-06-28', 'MAJOR', 'Tain nan lao dong nhe', 'RESOLVED', 'Bui Van G'),
(3, '2026-06-15', 'MINOR', 'Hong thiet bi do luong', 'RESOLVED', 'Dang Thi H'),
(1, '2026-06-01', 'MINOR', 'Chay nho trong kho hang', 'RESOLVED', 'Ngo Van I'),
(7, '2026-05-20', 'MAJOR', 'Long dat o mui khai thac', 'RESOLVED', 'Duong Thi J'),
(2, '2026-05-10', 'MINOR', 'Roi vat dung tu do', 'RESOLVED', 'Phan Van K'),
(9, '2026-04-25', 'CRITICAL', 'Ha sap lon, di tinh', 'CLOSED', 'Mai Thi L'),
(4, '2026-04-10', 'MINOR', 'Hong duong ong nuoc', 'RESOLIZED', 'Thanh Van M'),
(6, '2026-03-15', 'MAJOR', 'Tieeng on vuot tieu chuan', 'RESOLVED', 'Ly Thi N'),
(10, '2026-03-01', 'MINOR', 'Roi nho', 'RESOLIZED', 'Truong Van O');
