-- Migration: 0003_seed_plans
-- Description: Seeds the three initial subscription plans using fixed,
--              well-known UUIDs, so application code (e.g. the User
--              Service, when it assigns users.plan_id) can reference a
--              specific plan by constant instead of a lookup query.
--              No user rows are seeded here.

INSERT INTO plans (id, name, price, description) VALUES
    ('11111111-1111-1111-1111-111111111111', 'Free',    0.00,   'Free tier with standard booking access.'),
    ('22222222-2222-2222-2222-222222222222', 'Pro',     199.00, 'Pro tier with priority booking access and higher rate limits.'),
    ('33333333-3333-3333-3333-333333333333', 'Premium', 499.00, 'Premium tier with the highest rate limits and full feature access.')
ON CONFLICT (id) DO NOTHING;
