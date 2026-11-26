const entries = [
  ['battir', 'بتير', 'بيت لحم', [31.728, 35.139]],
  ['lifta', 'لفتا', 'القدس', [31.796, 35.192]],
  ['deir-ghassaneh', 'دير غسانة', 'رام الله والبيرة', [32.038, 35.104]],
  ['artas', 'أرطاس', 'بيت لحم', [31.689, 35.187]],
  ['sebatya', 'سبسطية', 'نابلس', [32.277, 35.189]],
  ['ein-arik', 'عين عريك', 'رام الله والبيرة', [31.906, 35.143]],
  ['beit-ummar', 'بيت أمّر', 'الخليل', [31.623, 35.105]],
  ['beita', 'بيتا', 'نابلس', null],
]

export const villages = entries.map(([id, name, governorate, coordinates], index) => ({
  id,
  name,
  governorate,
  coordinates,
  featured: index < 3,
  contentStatus: 'demo',
  overview: `صفحة تعريفية تجريبية لقرية ${name}، تعرض أقسام الملف المخصصة لتوثيق حياة القرية وتراثها.`,
  population: null,
  history: null,
  heritage: index < 3 ? 'موضوعات مقترحة للتوثيق: العمارة الحجرية، الحرف التقليدية، والروايات الشفوية.' : null,
  agriculture: index === 0 ? 'موضوعات مقترحة للتوثيق: أشجار الزيتون، المواسم الزراعية، وطرق العناية بالأرض.' : null,
  community: index === 1 ? 'موضوعات مقترحة للتوثيق: المرافق العامة والمبادرات المجتمعية.' : null,
}))
