DELETE FROM role_permissions role_permission
USING permissions permission
WHERE role_permission.permission_id = permission.id
  AND role_permission.role_code = 'AGENT'
  AND permission.code = 'incident-type.create';
