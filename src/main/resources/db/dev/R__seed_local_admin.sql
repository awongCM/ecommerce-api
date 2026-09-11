-- Local-only admin for the `dev` profile (H2). Not applied in docker/prod.
-- Login: admin@localhost / adminpass  (BCrypt cost 12 — not for production)
INSERT INTO customers (first_name, last_name, email, password_hash, created_at, updated_at)
SELECT 'Local', 'Admin', 'admin@localhost',
       '$2b$12$rB.2XN13jYGl58USMyxYsOOF/vDDMFHJIDmBQHvYtjQWF/axNjxH6',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM customers WHERE email = 'admin@localhost');

INSERT INTO customer_roles (customer_id, roles)
SELECT c.id, 'CUSTOMER' FROM customers c
WHERE c.email = 'admin@localhost'
  AND NOT EXISTS (
    SELECT 1 FROM customer_roles cr
    WHERE cr.customer_id = c.id AND cr.roles = 'CUSTOMER'
  );

INSERT INTO customer_roles (customer_id, roles)
SELECT c.id, 'ADMIN' FROM customers c
WHERE c.email = 'admin@localhost'
  AND NOT EXISTS (
    SELECT 1 FROM customer_roles cr
    WHERE cr.customer_id = c.id AND cr.roles = 'ADMIN'
  );
