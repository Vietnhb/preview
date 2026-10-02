-- One curriculum only: remove every topic, module, level and lesson that is not part of
-- curriculum/catalog.json (the Vietnamese 2018 programme, 21 topics, 114 lessons).
-- This deletes the retired English curriculum (circuits, electromagnetism, modern-physics,
-- practical-data, thermal, optics), the old modules left inside current topics and the
-- generated "mvp-core" bootstrap, instead of keeping them switched off in the manager screen.
-- Saved library items and problems that pointed at a removed lesson are kept without a lesson.

CREATE TEMP TABLE curriculum_keep (topic_slug text, module_slug text, level_name text, lesson_slug text);
INSERT INTO curriculum_keep (topic_slug, module_slug, level_name, lesson_slug) VALUES
    ('kinematics', 'mo-ta-chuyen-dong', 'Lớp 10', 'toc-do-van-toc-do-dich-chuyen-do-dich-chuyen-va-van-toc-tong-hop'),
    ('kinematics', 'mo-ta-chuyen-dong', 'Lớp 10', 'do-thi-do-dich-chuyen-thoi-gian'),
    ('kinematics', 'chuyen-dong-bien-doi', 'Lớp 10', 'gia-toc'),
    ('kinematics', 'chuyen-dong-bien-doi', 'Lớp 10', 'do-thi-van-toc-thoi-gian'),
    ('kinematics', 'chuyen-dong-bien-doi', 'Lớp 10', 'chuyen-dong-thang-bien-doi-deu'),
    ('kinematics', 'chuyen-dong-bien-doi', 'Lớp 10', 'roi-tu-do'),
    ('kinematics', 'chuyen-dong-bien-doi', 'Lớp 10', 'chuyen-dong-nem'),
    ('dynamics', 'ba-dinh-luat-newton-ve-chuyen-dong', 'Lớp 10', 'f-ma-quan-tinh-trong-luc-roi-trong-truong-trong-luc'),
    ('dynamics', 'mot-so-luc-trong-thuc-tien', 'Lớp 10', 'trong-luc-ma-sat-luc-can-luc-nang-luc-cang-day'),
    ('dynamics', 'can-bang-luc-moment-luc', 'Lớp 10', 'tong-hop-phan-tich-luc'),
    ('dynamics', 'can-bang-luc-moment-luc', 'Lớp 10', 'moment-luc'),
    ('dynamics', 'can-bang-luc-moment-luc', 'Lớp 10', 'dieu-kien-can-bang'),
    ('dynamics', 'khoi-luong-rieng-ap-suat-chat-long', 'Lớp 10', 'p-g-h'),
    ('work-energy-power', 'cong-va-nang-luong', 'Lớp 10', 'a-f-s-cos'),
    ('work-energy-power', 'cong-va-nang-luong', 'Lớp 10', 'truyen-nang-luong-bang-thuc-hien-cong'),
    ('work-energy-power', 'dong-nang-va-the-nang', 'Lớp 10', 'wd-mv-2'),
    ('work-energy-power', 'dong-nang-va-the-nang', 'Lớp 10', 'wt-mgh'),
    ('work-energy-power', 'dong-nang-va-the-nang', 'Lớp 10', 'bao-toan-co-nang'),
    ('work-energy-power', 'cong-suat-va-hieu-suat', 'Lớp 10', 'p-a-t-fv'),
    ('work-energy-power', 'cong-suat-va-hieu-suat', 'Lớp 10', 'h-a-co-ich-a-toan-phan'),
    ('momentum', 'dinh-nghia-dong-luong', 'Lớp 10', 'p-mv'),
    ('momentum', 'bao-toan-dong-luong', 'Lớp 10', 'he-kin-tong-dong-luong-khong-doi'),
    ('momentum', 'dong-luong-va-va-cham', 'Lớp 10', 'f-p-t'),
    ('momentum', 'dong-luong-va-va-cham', 'Lớp 10', 'nang-luong-trong-va-cham'),
    ('circular-motion', 'dong-hoc-cua-chuyen-dong-tron-deu', 'Lớp 10', 'radian-toc-do-goc'),
    ('circular-motion', 'gia-toc-huong-tam-va-luc-huong-tam', 'Lớp 10', 'a-r-f-mr'),
    ('solid-deformation', 'bien-dang-keo-va-bien-dang-nen-dac-tinh-cua-lo-xo', 'Lớp 10', 'gioi-han-dan-hoi-do-dan-do-cung'),
    ('solid-deformation', 'dinh-luat-hooke', 'Lớp 10', 'f-k-l'),
    ('earth-and-sky', 'xac-dinh-phuong-huong', 'Lớp 10', 'chom-sao-sao-bac-cuc'),
    ('earth-and-sky', 'chuyen-dong-nhin-thay-cua-mot-so-thien-the', 'Lớp 10', 'mat-troi-mat-trang-kim-tinh-thuy-tinh'),
    ('earth-and-sky', 'chuyen-dong-nhin-thay-cua-mot-so-thien-the', 'Lớp 10', 'mo-hinh-copernic'),
    ('earth-and-sky', 'mot-so-hien-tuong-thien-van', 'Lớp 10', 'nhat-thuc-nguyet-thuc-thuy-trieu'),
    ('gravitational-field', 'khai-niem-truong-hap-dan', 'Lớp 11', 'moi-vat-co-khoi-luong-tao-truong-hap-dan'),
    ('gravitational-field', 'luc-hap-dan', 'Lớp 11', 'f-gm-m-r'),
    ('gravitational-field', 'cuong-do-truong-hap-dan', 'Lớp 11', 'g-gm-r'),
    ('gravitational-field', 'the-hap-dan-va-the-nang-hap-dan', 'Lớp 11', 'gm-r'),
    ('gravitational-field', 'the-hap-dan-va-the-nang-hap-dan', 'Lớp 11', 've-tinh-dia-tinh'),
    ('gravitational-field', 'the-hap-dan-va-the-nang-hap-dan', 'Lớp 11', 'toc-do-vu-tru-cap-1'),
    ('oscillations', 'dao-dong-dieu-hoa', 'Lớp 11', 'bien-do-chu-ki-tan-so-do-lech-pha'),
    ('oscillations', 'dao-dong-dieu-hoa', 'Lớp 11', 'a-x'),
    ('oscillations', 'dao-dong-dieu-hoa', 'Lớp 11', 'nang-luong'),
    ('oscillations', 'dao-dong-tat-dan-hien-tuong-cong-huong', 'Lớp 11', 'dao-dong-cuong-buc-cong-huong'),
    ('waves', 'mo-ta-song', 'Lớp 11', 'a-f-v-cuong-do'),
    ('waves', 'mo-ta-song', 'Lớp 11', 'v-f'),
    ('waves', 'song-doc-va-song-ngang', 'Lớp 11', 'so-sanh'),
    ('waves', 'song-doc-va-song-ngang', 'Lớp 11', 'do-tan-so-am'),
    ('waves', 'song-dien-tu', 'Lớp 11', 'cung-toc-do-trong-chan-khong'),
    ('waves', 'song-dien-tu', 'Lớp 11', 'thang-song-dien-tu'),
    ('waves', 'giao-thoa-song-ket-hop', 'Lớp 11', 'dieu-kien-giao-thoa'),
    ('waves', 'giao-thoa-song-ket-hop', 'Lớp 11', 'i-d-a'),
    ('waves', 'song-dung', 'Lớp 11', 'nut-bung'),
    ('waves', 'do-toc-do-truyen-am', 'Lớp 11', 'phuong-an-do'),
    ('radio-communication', 'bien-dieu', 'Lớp 11', 'am-fm'),
    ('radio-communication', 'bien-dieu', 'Lớp 11', 'tan-so-buoc-song-cac-kenh'),
    ('radio-communication', 'tin-hieu-tuong-tu-va-tin-hieu-so', 'Lớp 11', 'adc-dac'),
    ('radio-communication', 'suy-giam-tin-hieu', 'Lớp 11', 'db-db-don-vi-do-dai'),
    ('electric-field', 'luc-dien-tuong-tac-giua-cac-dien-tich', 'Lớp 11', 'dinh-luat-coulomb'),
    ('electric-field', 'khai-niem-dien-truong', 'Lớp 11', 'e-q-4-r'),
    ('electric-field', 'dien-truong-deu', 'Lớp 11', 'e-u-d'),
    ('electric-field', 'dien-truong-deu', 'Lớp 11', 'dien-tich-bay-vao-dien-truong'),
    ('electric-field', 'dien-the-va-the-nang-dien', 'Lớp 11', 'v-a-q'),
    ('electric-field', 'tu-dien-va-dien-dung', 'Lớp 11', 'ghep-tu'),
    ('electric-field', 'tu-dien-va-dien-dung', 'Lớp 11', 'nang-luong-tu-dien'),
    ('electric-current', 'cuong-do-dong-dien', 'Lớp 11', 'i-snve'),
    ('electric-current', 'cuong-do-dong-dien', 'Lớp 11', 'coulomb'),
    ('electric-current', 'mach-dien-va-dien-tro', 'Lớp 11', 'dinh-luat-ohm'),
    ('electric-current', 'mach-dien-va-dien-tro', 'Lớp 11', 'i-u'),
    ('electric-current', 'mach-dien-va-dien-tro', 'Lớp 11', 'dien-tro-nhiet'),
    ('electric-current', 'mach-dien-va-dien-tro', 'Lớp 11', 'suat-dien-dong-dien-tro-trong'),
    ('electric-current', 'nang-luong-dien-cong-suat-dien', 'Lớp 11', 'a-uit'),
    ('electric-current', 'nang-luong-dien-cong-suat-dien', 'Lớp 11', 'p-ui'),
    ('electronics', 'khuech-dai-thuat-toan', 'Lớp 11', 'cam-bien-ldr-dien-tro-nhiet'),
    ('electronics', 'khuech-dai-thuat-toan', 'Lớp 11', 'op-amp-li-tuong'),
    ('electronics', 'thiet-bi-dau-ra', 'Lớp 11', 'op-amp-relay-led-dong-ho-do'),
    ('electronics', 'thiet-bi-cam-bien', 'Lớp 11', 'ung-dung-nguyen-tac-hoat-dong'),
    ('thermal-physics', 'su-chuyen-the', 'Lớp 12', 'nong-chay-hoa-hoi'),
    ('thermal-physics', 'noi-nang-dinh-luat-1-cua-nhiet-dong-luc-hoc', 'Lớp 12', 'u-a-q'),
    ('thermal-physics', 'thang-nhiet-do-nhiet-ke', 'Lớp 12', 'celsius-kelvin'),
    ('thermal-physics', 'thang-nhiet-do-nhiet-ke', 'Lớp 12', 'chieu-truyen-nhiet'),
    ('thermal-physics', 'nhiet-dung-rieng-nhiet-nong-chay-rieng-nhiet-hoa-hoi-rieng', 'Lớp 12', 'q-mc-t'),
    ('thermal-physics', 'nhiet-dung-rieng-nhiet-nong-chay-rieng-nhiet-hoa-hoi-rieng', 'Lớp 12', 'q-m'),
    ('thermal-physics', 'nhiet-dung-rieng-nhiet-nong-chay-rieng-nhiet-hoa-hoi-rieng', 'Lớp 12', 'q-ml'),
    ('ideal-gas', 'mo-hinh-dong-hoc-phan-tu-chat-khi', 'Lớp 12', 'chuyen-dong-brown'),
    ('ideal-gas', 'mo-hinh-dong-hoc-phan-tu-chat-khi', 'Lớp 12', 'gia-thuyet-dong-hoc-phan-tu'),
    ('ideal-gas', 'phuong-trinh-trang-thai', 'Lớp 12', 'boyle-charles-pv-nrt'),
    ('ideal-gas', 'ap-suat-khi-theo-mo-hinh-dong-hoc-phan-tu', 'Lớp 12', 'p-1-3-nmv'),
    ('ideal-gas', 'dong-nang-phan-tu', 'Lớp 12', 'k-r-n'),
    ('ideal-gas', 'dong-nang-phan-tu', 'Lớp 12', 'wd-3-2-kt'),
    ('magnetic-field', 'khai-niem-tu-truong', 'Lớp 12', 'duong-suc-tu'),
    ('magnetic-field', 'luc-tu-tac-dung-len-doan-day-dan-mang-dong-dien-cam-ung-tu', 'Lớp 12', 'f-bilsin'),
    ('magnetic-field', 'luc-tu-tac-dung-len-doan-day-dan-mang-dong-dien-cam-ung-tu', 'Lớp 12', 'tesla'),
    ('magnetic-field', 'tu-thong-cam-ung-dien-tu', 'Lớp 12', 'faraday-lenz'),
    ('magnetic-field', 'tu-thong-cam-ung-dien-tu', 'Lớp 12', 'tao-dong-dien-xoay-chieu'),
    ('alternating-current', 'cac-dac-trung-cua-dong-dien-xoay-chieu', 'Lớp 12', 'gia-tri-hieu-dung'),
    ('alternating-current', 'cac-dac-trung-cua-dong-dien-xoay-chieu', 'Lớp 12', 'cong-suat'),
    ('alternating-current', 'cac-dac-trung-cua-dong-dien-xoay-chieu', 'Lớp 12', 'mach-rlc'),
    ('alternating-current', 'may-bien-ap', 'Lớp 12', 'truyen-tai-dien-nang'),
    ('alternating-current', 'chinh-luu-dong-dien-xoay-chieu', 'Lớp 12', 'diot'),
    ('alternating-current', 'chinh-luu-dong-dien-xoay-chieu', 'Lớp 12', 'chinh-luu-nua-chu-ki-ca-chu-ki'),
    ('nuclear-physics', 'cau-truc-hat-nhan', 'Lớp 12', 'tan-xa'),
    ('nuclear-physics', 'cau-truc-hat-nhan', 'Lớp 12', 'ki-hieu-hat-nhan'),
    ('nuclear-physics', 'do-hut-khoi-va-nang-luong-lien-ket-hat-nhan', 'Lớp 12', 'e-mc'),
    ('nuclear-physics', 'do-hut-khoi-va-nang-luong-lien-ket-hat-nhan', 'Lớp 12', 'phan-hach-nhiet-hach'),
    ('nuclear-physics', 'su-phong-xa-va-chu-ki-ban-ra', 'Lớp 12', 'h-n'),
    ('nuclear-physics', 'su-phong-xa-va-chu-ki-ban-ra', 'Lớp 12', 'x-x-e-t'),
    ('medical-physics', 'ban-chat-va-cach-tao-ra-tia-x', 'Lớp 12', 'tao-dieu-khien-suy-giam-tia-x'),
    ('medical-physics', 'chan-doan-bang-tia-x', 'Lớp 12', 'lieu-chieu-do-tuong-phan'),
    ('medical-physics', 'chan-doan-bang-sieu-am', 'Lớp 12', 'tao-anh-sieu-am'),
    ('medical-physics', 'chup-cat-lop-cong-huong-tu', 'Lớp 12', 'ct-mri'),
    ('quantum-physics', 'hieu-ung-quang-dien-va-nang-luong-cua-photon', 'Lớp 12', 'e-hf'),
    ('quantum-physics', 'hieu-ung-quang-dien-va-nang-luong-cua-photon', 'Lớp 12', 'phuong-trinh-einstein'),
    ('quantum-physics', 'luong-tinh-song-hat', 'Lớp 12', 'h-p'),
    ('quantum-physics', 'quang-pho-vach-cua-nguyen-tu', 'Lớp 12', 'hf-e-e'),
    ('quantum-physics', 'vung-nang-luong', 'Lớp 12', 'dien-tro-kim-loai-ban-dan-ldr');

CREATE TEMP TABLE lessons_to_remove AS
SELECT ls.id
FROM lessons ls
JOIN grade_levels gl ON gl.id = ls.level_id
JOIN content_modules m ON m.id = gl.module_id
JOIN topics t ON t.id = m.topic_id
WHERE NOT EXISTS (SELECT 1 FROM curriculum_keep k
                  WHERE k.topic_slug = t.slug AND k.module_slug = m.slug
                    AND k.level_name = gl.name AND k.lesson_slug = ls.slug);

UPDATE library_items SET lesson_id = NULL WHERE lesson_id IN (SELECT id FROM lessons_to_remove);
UPDATE problem_submissions SET lesson_id = NULL WHERE lesson_id IN (SELECT id FROM lessons_to_remove);
DELETE FROM lessons WHERE id IN (SELECT id FROM lessons_to_remove);

DELETE FROM grade_levels gl
USING content_modules m, topics t
WHERE m.id = gl.module_id AND t.id = m.topic_id
  AND NOT EXISTS (SELECT 1 FROM curriculum_keep k
                  WHERE k.topic_slug = t.slug AND k.module_slug = m.slug AND k.level_name = gl.name);

DELETE FROM content_modules m
USING topics t
WHERE t.id = m.topic_id
  AND NOT EXISTS (SELECT 1 FROM curriculum_keep k WHERE k.topic_slug = t.slug AND k.module_slug = m.slug);

DELETE FROM topics t
WHERE NOT EXISTS (SELECT 1 FROM curriculum_keep k WHERE k.topic_slug = t.slug);

-- Everything that remains is the current catalog; make sure none of it is left switched off.
UPDATE topics SET enabled = TRUE WHERE enabled = FALSE;
UPDATE content_modules SET active = TRUE WHERE active = FALSE;
UPDATE grade_levels SET active = TRUE WHERE active = FALSE;
UPDATE lessons SET active = TRUE WHERE active = FALSE;

DROP TABLE lessons_to_remove;
DROP TABLE curriculum_keep;
