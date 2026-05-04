import jsPDF from 'jspdf';
import autoTable from 'jspdf-autotable';
import { ServiceOrder } from '@/src/types';

interface JsPDFWithAutoTable extends jsPDF {
  lastAutoTable: { finalY: number };
}

export function generateInvoicePDF(order: ServiceOrder, separadoPor = 'Operador'): void {
  const doc = new jsPDF() as JsPDFWithAutoTable;

  // Header background
  doc.setFillColor(0, 50, 139);
  doc.rect(0, 0, 210, 38, 'F');

  doc.setTextColor(255, 255, 255);
  doc.setFontSize(16);
  doc.setFont('helvetica', 'bold');
  doc.text('APSEN FARMACÊUTICOS S.A.', 14, 14);

  doc.setFontSize(9);
  doc.setFont('helvetica', 'normal');
  doc.text('PEDIDO DE SEPARAÇÃO — DOCUMENTO OFICIAL', 14, 22);
  doc.text('CNPJ: 61.797.924/0001-90  |  Valinhos, SP', 14, 29);

  doc.setFontSize(20);
  doc.setFont('helvetica', 'bold');
  doc.text(order.id, 196, 18, { align: 'right' });

  doc.setFontSize(8);
  doc.setFont('helvetica', 'normal');
  doc.text(`Emitido: ${new Date().toLocaleString('pt-BR')}`, 196, 30, { align: 'right' });

  // Reset text
  doc.setTextColor(0, 0, 0);

  // Section: Order Info
  let y = 50;
  doc.setFont('helvetica', 'bold');
  doc.setFontSize(9);
  doc.setTextColor(0, 50, 139);
  doc.text('INFORMAÇÕES DA ORDEM', 14, y);
  doc.setDrawColor(0, 50, 139);
  doc.setLineWidth(0.4);
  doc.line(14, y + 2, 196, y + 2);

  doc.setTextColor(0, 0, 0);
  doc.setFontSize(9);

  const col1x = 14;
  const col2x = 60;
  const col3x = 120;
  const col4x = 155;

  y += 10;
  doc.setFont('helvetica', 'bold');
  doc.text('Cliente:', col1x, y);
  doc.setFont('helvetica', 'normal');
  doc.text(order.client, col2x, y);

  doc.setFont('helvetica', 'bold');
  doc.text('Prioridade:', col3x, y);
  doc.setFont('helvetica', 'normal');
  doc.text(order.priority, col4x, y);

  y += 8;
  doc.setFont('helvetica', 'bold');
  doc.text('Destino:', col1x, y);
  doc.setFont('helvetica', 'normal');
  doc.text(order.destination, col2x, y);

  doc.setFont('helvetica', 'bold');
  doc.text('Status:', col3x, y);
  doc.setFont('helvetica', 'normal');
  doc.text(order.status, col4x, y);

  y += 8;
  doc.setFont('helvetica', 'bold');
  doc.text('Data:', col1x, y);
  doc.setFont('helvetica', 'normal');
  doc.text(order.date, col2x, y);

  if (order.temperature) {
    doc.setFont('helvetica', 'bold');
    doc.text('Temperatura:', col3x, y);
    doc.setFont('helvetica', 'normal');
    doc.text(order.temperature, col4x, y);
  }

  y += 8;
  doc.setFont('helvetica', 'bold');
  doc.text('Origem:', col1x, y);
  doc.setFont('helvetica', 'normal');
  doc.text(order.origin ?? 'Centro de Distribuição Valinhos — SP', col2x, y);

  if (order.eta) {
    doc.setFont('helvetica', 'bold');
    doc.text('ETA:', col3x, y);
    doc.setFont('helvetica', 'normal');
    doc.text(`${order.eta} — Hub SP`, col4x, y);
  }

  // Section: Manifest
  y += 16;
  doc.setFont('helvetica', 'bold');
  doc.setFontSize(9);
  doc.setTextColor(0, 50, 139);
  doc.text('MANIFESTO DE ITENS', 14, y);
  doc.line(14, y + 2, 196, y + 2);
  doc.setTextColor(0, 0, 0);

  const items = order.items ?? [
    { name: 'Donaren®', subName: 'Trazodona HCl', qty: '850', batch: '#B24-APS-442', coldChain: false },
    { name: 'Flancox®', subName: 'Etodolaco', qty: '1.200', batch: '#B24-APS-091', coldChain: false },
    { name: 'Alois®', subName: 'Memantina HCl', qty: '2.400', batch: '#B24-APS-218', coldChain: true },
    { name: 'Miosan', subName: 'Ciclobenzaprina HCl', qty: '600', batch: '#B24-APS-331', coldChain: false },
    { name: 'Atentah', subName: 'Atomoxetina HCl', qty: '900', batch: '#B24-APS-157', coldChain: false },
  ];

  autoTable(doc, {
    startY: y + 4,
    head: [['Medicamento', 'Substancia Ativa', 'No. Lote', 'Qtd. Un.', 'Caixa Estoque', 'Cadeia Fria']],
    body: items.map(item => [
      item.name,
      item.subName,
      item.batch,
      item.qty,
      item.stockBox ?? '—',
      item.coldChain ? 'Sim (2-8 C)' : 'Nao',
    ]),
    headStyles: { fillColor: [0, 50, 139], fontSize: 8, fontStyle: 'bold', textColor: 255 },
    bodyStyles: { fontSize: 8 },
    alternateRowStyles: { fillColor: [245, 247, 252] },
    margin: { left: 14, right: 14 },
    columnStyles: {
      0: { fontStyle: 'bold' },
      5: { halign: 'center' },
    },
  });

  y = doc.lastAutoTable.finalY + 12;

  // Section: Route
  doc.setFont('helvetica', 'bold');
  doc.setFontSize(9);
  doc.setTextColor(0, 50, 139);
  doc.text('ROTA DE ENVIO', 14, y);
  doc.line(14, y + 2, 196, y + 2);
  doc.setTextColor(0, 0, 0);
  doc.setFont('helvetica', 'normal');
  doc.setFontSize(8);

  y += 10;
  doc.text(`Origem : ${order.origin ?? 'CD Valinhos — Valinhos, SP'}`, 14, y);
  y += 7;
  doc.text('Parada : Checkpoint Rodovia Anhanguera km 87 (verificacao de temperatura)', 14, y);
  y += 7;
  doc.text(`Destino: ${order.destination}`, 14, y);
  y += 7;
  doc.text(`ETA    : ${order.eta ?? '14:35'} — Hub SP`, 14, y);

  // Section: Requirements
  y += 14;
  doc.setFont('helvetica', 'bold');
  doc.setFontSize(9);
  doc.setTextColor(0, 50, 139);
  doc.text('REQUISITOS ESPECIAIS', 14, y);
  doc.line(14, y + 2, 196, y + 2);
  doc.setTextColor(0, 0, 0);
  doc.setFont('helvetica', 'normal');
  doc.setFontSize(8);

  y += 10;
  doc.text('- Monitoramento de temperatura: 2,0 C a 8,0 C (cadeia fria obrigatoria)', 14, y);
  y += 7;
  doc.text('- Protocolo de manuseio: Nivel 4 — Conformidade Bio-Hazard', 14, y);
  y += 7;
  doc.text('- Lacre obrigatorio com codigo rastreavelr', 14, y);
  y += 7;
  doc.text('- Divergencias bloqueiam expedicao ate validacao do supervisor', 14, y);

  // Section: Signatures
  y += 16;
  if (y > 230) {
    doc.addPage();
    y = 20;
  }

  doc.setFont('helvetica', 'bold');
  doc.setFontSize(9);
  doc.setTextColor(0, 50, 139);
  doc.text('ASSINATURAS E RESPONSABILIDADES', 14, y);
  doc.line(14, y + 2, 196, y + 2);
  doc.setTextColor(0, 0, 0);
  doc.setFont('helvetica', 'normal');
  doc.setFontSize(8);

  y += 12;
  doc.text(`Separado por: ${separadoPor}`, 14, y);
  y += 14;
  doc.line(14, y, 90, y);
  doc.text('Assinatura do Operador de Separacao', 14, y + 5);

  doc.text('Responsavel pela expedicao:', 110, y - 14);
  doc.line(110, y, 196, y);
  doc.text('Assinatura do Supervisor / Admin', 110, y + 5);

  y += 18;
  doc.text('Data/Hora da expedicao: ____/____/______  ____:____', 14, y);

  // Footer
  const ph = doc.internal.pageSize.height;
  doc.setFillColor(0, 50, 139);
  doc.rect(0, ph - 14, 210, 14, 'F');
  doc.setTextColor(255, 255, 255);
  doc.setFontSize(7);
  doc.text(
    'Documento confidencial — uso interno Apsen Farmaceuticos S.A. | Gerido pelo Apsen App',
    105, ph - 7, { align: 'center' }
  );
  doc.text(`Emitido em: ${new Date().toLocaleString('pt-BR')}`, 105, ph - 3, { align: 'center' });

  doc.save(`Pedido_Separacao_${order.id}.pdf`);
}
