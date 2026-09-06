-- 删除 file_resource 上的唯一键 uk_user_file_name_deleted
-- 该键导致用户删除同名文件后无法重新上传
-- file_path 使用 UUID 已保证物理路径唯一，无需此业务键
ALTER TABLE file_resource DROP INDEX uk_user_file_name_deleted;
