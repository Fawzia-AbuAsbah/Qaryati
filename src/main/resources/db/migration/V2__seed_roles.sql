
INSERT INTO public.roles (code, description)
VALUES
    ('VIEWER', 'Can view and search village information'),
    ('EDITOR', 'Can add and edit village information and attach sources'),
    ('ADMIN', 'Can manage users and roles and review information changes')
    ON CONFLICT (code) DO NOTHING;
