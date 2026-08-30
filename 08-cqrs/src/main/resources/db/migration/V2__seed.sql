-- Deterministic seed data. The tests freeze the clock at 2026-09-01, so
-- "overdue" below always means: due_on before 2026-09-01 and not returned.
--
-- Member roles (keep these stable — the tests rely on them):
--   1  Maya Chen        rich history, the pinned dashboard (12 loans)
--   2  Jonas Weber      favorite-author tie: Eric Evans vs Vaughn Vernon
--   3  Priya Sharma     heavy reader, incl. an exactly-on-time return
--   4  Tomasz Kowalski  everything returned, favorite-author 5-way tie
--   5  Aino Virtanen    brand new, zero loans
--   6  Marco Rossi      at the 5-open-loan limit; holds the only Accelerate copy
--   7  Lena Novak       mutated by the behavior tests (borrow flows)
--   8  David Okafor     mutated by the behavior tests and checkpoint 2
--   9  Sofia Lindqvist  checkpoint 4 subject (staleness)
--   10 Ana Costa        checkpoint 5 subject (replay)

INSERT INTO members (id, name, email, joined_on) VALUES
    (1,  'Maya Chen',        'maya.chen@example.com',        '2024-05-14'),
    (2,  'Jonas Weber',      'jonas.weber@example.com',      '2024-09-02'),
    (3,  'Priya Sharma',     'priya.sharma@example.com',     '2023-11-20'),
    (4,  'Tomasz Kowalski',  'tomasz.kowalski@example.com',  '2025-01-08'),
    (5,  'Aino Virtanen',    'aino.virtanen@example.com',    '2026-08-15'),
    (6,  'Marco Rossi',      'marco.rossi@example.com',      '2024-03-30'),
    (7,  'Lena Novak',       'lena.novak@example.com',       '2025-06-11'),
    (8,  'David Okafor',     'david.okafor@example.com',     '2025-10-04'),
    (9,  'Sofia Lindqvist',  'sofia.lindqvist@example.com',  '2024-12-01'),
    (10, 'Ana Costa',        'ana.costa@example.com',        '2025-04-22');

INSERT INTO books (id, isbn, title, author, copies) VALUES
    (1,  '978-0-13-475759-9', 'Refactoring',                                   'Martin Fowler',    3),
    (2,  '978-0-32-112742-6', 'Patterns of Enterprise Application Architecture','Martin Fowler',   2),
    (3,  '978-0-32-112521-7', 'Domain-Driven Design',                          'Eric Evans',       2),
    (4,  '978-0-32-183457-7', 'Implementing Domain-Driven Design',             'Vaughn Vernon',    2),
    (5,  '978-0-13-235088-4', 'Clean Code',                                    'Robert C. Martin', 3),
    (6,  '978-1-68-050239-8', 'Release It!',                                   'Michael Nygard',   2),
    (7,  '978-1-44-937332-0', 'Designing Data-Intensive Applications',         'Martin Kleppmann', 3),
    (8,  '978-0-13-117705-5', 'Working Effectively with Legacy Code',          'Michael Feathers', 2),
    (9,  '978-1-73-210221-1', 'A Philosophy of Software Design',               'John Ousterhout',  2),
    (10, '978-0-32-160191-9', 'Continuous Delivery',                           'Jez Humble',       2),
    (11, '978-0-13-595705-9', 'The Pragmatic Programmer',                      'Andrew Hunt',      2),
    (12, '978-1-49-203402-5', 'Building Microservices',                        'Sam Newman',       2),
    (13, '978-1-94-278833-1', 'Accelerate',                                    'Nicole Forsgren',  1),
    (14, '978-1-94-278881-2', 'Team Topologies',                               'Matthew Skelton',  2),
    (15, '978-0-32-120068-6', 'Enterprise Integration Patterns',               'Gregor Hohpe',     2);

INSERT INTO loans (id, member_id, book_id, borrowed_on, due_on, returned_on) VALUES
    -- Maya Chen: 8 returned (2 late), 4 open (1 overdue); favorite Martin Fowler (3x)
    (1,  1, 1,  '2026-03-02', '2026-03-23', '2026-03-20'),
    (2,  1, 3,  '2026-03-25', '2026-04-15', '2026-04-20'),  -- late
    (3,  1, 5,  '2026-04-01', '2026-04-22', '2026-04-10'),
    (4,  1, 7,  '2026-04-15', '2026-05-06', '2026-05-02'),
    (5,  1, 2,  '2026-05-04', '2026-05-25', '2026-06-01'),  -- late
    (6,  1, 9,  '2026-05-20', '2026-06-10', '2026-06-05'),
    (7,  1, 1,  '2026-06-08', '2026-06-29', '2026-06-27'),
    (8,  1, 11, '2026-06-20', '2026-07-11', '2026-07-01'),
    (9,  1, 12, '2026-07-25', '2026-08-15', NULL),           -- open, overdue
    (10, 1, 6,  '2026-08-12', '2026-09-02', NULL),           -- open
    (11, 1, 8,  '2026-08-18', '2026-09-08', NULL),           -- open
    (12, 1, 14, '2026-08-25', '2026-09-15', NULL),           -- open

    -- Jonas Weber: Evans 2x vs Vernon 2x -> alphabetical tie-break
    (13, 2, 3,  '2026-02-03', '2026-02-24', '2026-02-20'),
    (14, 2, 4,  '2026-03-05', '2026-03-26', '2026-03-25'),
    (15, 2, 3,  '2026-04-07', '2026-04-28', '2026-04-27'),
    (16, 2, 4,  '2026-05-11', '2026-06-01', '2026-06-10'),  -- late
    (17, 2, 10, '2026-06-15', '2026-07-06', '2026-07-02'),
    (18, 2, 7,  '2026-08-20', '2026-09-10', NULL),           -- open

    -- Priya Sharma: note loan 20 returned exactly on the due date (not late)
    (19, 3, 5,  '2026-01-12', '2026-02-02', '2026-01-30'),
    (20, 3, 6,  '2026-02-02', '2026-02-23', '2026-02-23'),  -- on time, boundary
    (21, 3, 7,  '2026-02-25', '2026-03-18', '2026-03-30'),  -- late
    (22, 3, 8,  '2026-03-20', '2026-04-10', '2026-04-05'),
    (23, 3, 9,  '2026-04-12', '2026-05-03', '2026-04-28'),
    (24, 3, 5,  '2026-05-05', '2026-05-26', '2026-05-20'),
    (25, 3, 13, '2026-05-28', '2026-06-18', '2026-06-15'),
    (26, 3, 15, '2026-06-20', '2026-07-11', '2026-07-13'),  -- late
    (27, 3, 1,  '2026-08-05', '2026-08-26', NULL),           -- open, overdue
    (28, 3, 4,  '2026-08-22', '2026-09-12', NULL),           -- open

    -- Tomasz Kowalski: all returned, one loan per author -> 5-way tie
    (29, 4, 10, '2025-11-03', '2025-11-24', '2025-11-21'),
    (30, 4, 11, '2025-12-01', '2025-12-22', '2026-01-05'),  -- late
    (31, 4, 12, '2026-01-10', '2026-01-31', '2026-01-28'),
    (32, 4, 14, '2026-02-15', '2026-03-08', '2026-03-01'),
    (33, 4, 15, '2026-03-12', '2026-04-02', '2026-04-30'),  -- late

    -- Marco Rossi: 5 open loans = at the limit; loan 34 holds the only
    -- Accelerate copy, which makes book 13 unavailable for everyone else
    (34, 6, 13, '2026-08-01', '2026-08-22', NULL),           -- open, overdue
    (35, 6, 2,  '2026-08-03', '2026-08-24', NULL),           -- open, overdue
    (36, 6, 6,  '2026-08-10', '2026-08-31', NULL),           -- open, overdue
    (37, 6, 10, '2026-08-15', '2026-09-05', NULL),           -- open
    (38, 6, 12, '2026-08-18', '2026-09-08', NULL),           -- open

    -- Lena Novak: one closed loan of history
    (39, 7, 1,  '2026-07-01', '2026-07-22', '2026-07-20'),

    -- Sofia Lindqvist (checkpoint 4): one returned, one open
    (40, 9, 3,  '2026-06-01', '2026-06-22', '2026-06-18'),
    (41, 9, 9,  '2026-08-14', '2026-09-04', NULL),           -- open

    -- Ana Costa (checkpoint 5): one returned
    (42, 10, 11, '2026-05-02', '2026-05-23', '2026-05-21');

-- Explicit ids above bypassed the identity sequences; move them out of the way.
SELECT setval(pg_get_serial_sequence('members', 'id'), 50);
SELECT setval(pg_get_serial_sequence('books', 'id'), 50);
SELECT setval(pg_get_serial_sequence('loans', 'id'), 100);
