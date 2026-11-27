INSERT INTO task_types (name)
VALUES ('Задача'), ('Ошибка'), ('Улучшение')
ON CONFLICT DO NOTHING;

INSERT INTO task_priorities (name)
VALUES ('Низкий'), ('Обычный'), ('Высокий')
ON CONFLICT DO NOTHING;
