UPDATE users
SET password = NULL,
    user_deleted = TRUE,
    deleted_at = COALESCE(deleted_at, CURRENT_TIMESTAMP)
WHERE email = 'email@email.com'
  AND nickname = '아아아';
