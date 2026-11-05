package view;

import java.awt.*;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;

import dao.DaoCarroBD;
import dao.DaoCarro;
import dao.DaoFuncionario;
import dao.DaoUsuario;
import dao.DaoVenda;
import dao.DaoVendasBD;
import entidade.Carro;
import entidade.Funcionario;
import entidade.Usuario;
import entidade.Vendas;
import service.ComissaoService;
import service.DashboardService;

/**
 * Interface principal da Concessionária — versão melhorada.
 *
 * Melhorias aplicadas em relação à versão original:
 *  - Painel de Relatórios completo (RelatorioPanel) adicionado como aba
 *  - Barra de busca/filtro em cada painel de tabela
 *  - Dashboard com card de receita total e separador visual
 *  - Tabela de vendas exibe nome do carro e do vendedor (não apenas IDs)
 *  - JToolBar substituindo o JPanel de botões soltos
 *  - Exclusão de venda restaura status do carro (via DaoCarroBD)
 *  - Constantes de cor e fonte centralizadas
 */
public class InterfaceGrafica extends JFrame {

    // ── DAOs ────────────────────────────────────────────────────────────
    private final DaoFuncionario funcionarioDAO = new DaoFuncionario();
    private final DaoCarro       carroDAO;
    private final DaoCarroBD     carroBD;
    private final DaoUsuario     usuarioDAO     = new DaoUsuario();
    private final DaoVenda       vendasDAO      = new DaoVendasBD();
    private final Usuario        usuarioLogado;
    private final DashboardService dashboardService = new DashboardService();

    // ── Models das tabelas ──────────────────────────────────────────────
    private final DefaultTableModel dtmCarros   = new DefaultTableModel(
            new Object[]{"ID", "Nome", "Modelo", "Ano", "Preço", "Status", "Nível"}, 0) {
        @Override public boolean isCellEditable(int r, int c) { return false; }
    };
    private final DefaultTableModel dtmFunc     = new DefaultTableModel(
            new Object[]{"ID", "Nome", "Salário"}, 0) {
        @Override public boolean isCellEditable(int r, int c) { return false; }
    };
    private final DefaultTableModel dtmUsuarios = new DefaultTableModel(
            new Object[]{"ID", "Username", "Papel", "Funcionário"}, 0) {
        @Override public boolean isCellEditable(int r, int c) { return false; }
    };
    // MELHORIA: exibe nome do carro e do vendedor em vez de IDs numéricos
    private final DefaultTableModel dtmVendas   = new DefaultTableModel(
            new Object[]{"ID", "Carro", "Vendedor", "Valor", "Comissão", "Data"}, 0) {
        @Override public boolean isCellEditable(int r, int c) { return false; }
    };

    // ── Cards do dashboard ──────────────────────────────────────────────
    private JLabel lblTotalCarros, lblDisponiveis, lblTotalFunc, lblTotalVendas, lblReceitaTotal, lblTaxaConversao;
    private JLabel lblVariacaoVendas, lblVariacaoReceita, lblDashPeriodo, lblDashAviso;
    private JComboBox<String> comboPeriodoDashboard;
    private DashboardService.Resultado ultimoDashboard;
    private DashboardCombinedChart graficoReceitaVendas;
    private DashboardDonutChart graficoEstoque;
    private DashboardSellerChart graficoVendedores;

    // ── Referência ao painel de relatórios ──────────────────────────────
    private RelatorioPanel relatorioPanel;

    // ── Paleta central (fonte de verdade para todo o tema) ──────────────
    static final Color COR_FUNDO      = new Color(12, 20, 40);
    static final Color COR_MENU       = new Color(15, 30, 60);
    static final Color COR_BOTAO      = new Color(0, 122, 204);
    static final Color COR_DESTAQUE   = new Color(241, 196, 15);
    static final Color COR_DANGER     = new Color(192, 57, 43);
    static final Color COR_BRANCO     = Color.WHITE;
    static final Color COR_TEXTO_SEC  = new Color(160, 190, 230);
    static final Font  FONTE_PADRAO   = new Font("Segoe UI", Font.PLAIN, 13);
    static final Font  FONTE_TITULO   = new Font("Segoe UI", Font.BOLD, 14);

    // ════════════════════════════════════════════════════════════════════
    // CONSTRUTOR
    // ════════════════════════════════════════════════════════════════════
    public InterfaceGrafica(DaoCarro daoCarro, Usuario usuarioLogado) {
        this.carroDAO      = daoCarro;
        this.carroBD       = (daoCarro instanceof DaoCarroBD) ? (DaoCarroBD) daoCarro : new DaoCarroBD();
        this.usuarioLogado = usuarioLogado;

        // Aplica o tema globalmente
        UIManager.put("Label.font",      FONTE_PADRAO);
        UIManager.put("Button.font",     FONTE_PADRAO);
        UIManager.put("Table.font",      FONTE_PADRAO);
        UIManager.put("ComboBox.font",   FONTE_PADRAO);
        UIManager.put("TextField.font",  FONTE_PADRAO);

        setTitle("Automanager — " + usuarioLogado.getUsername());
        setSize(1200, 700);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        // Barra lateral
        add(criarMenuLateral(), BorderLayout.WEST);

        // Abas
        JTabbedPane abas = criarAbas();
        add(abas, BorderLayout.CENTER);

        // Rodapé
        add(criarRodape(), BorderLayout.SOUTH);

        atualizarDashboard();
    }

    // ════════════════════════════════════════════════════════════════════
    // MENU LATERAL
    // ════════════════════════════════════════════════════════════════════
    private JPanel criarMenuLateral() {
        JPanel menu = new JPanel();
        menu.setBackground(COR_MENU);
        menu.setPreferredSize(new Dimension(200, 0));
        menu.setLayout(new BoxLayout(menu, BoxLayout.Y_AXIS));
        menu.setBorder(new EmptyBorder(20, 12, 20, 12));

        // Logo / título
        JLabel logo = new JLabel("AM");
        logo.setAlignmentX(Component.CENTER_ALIGNMENT);
        logo.setForeground(COR_DESTAQUE);
        logo.setFont(new Font("Segoe UI", Font.BOLD, 28));

        JLabel titulo = new JLabel("Automanager");
        titulo.setForeground(COR_BRANCO);
        titulo.setFont(FONTE_TITULO);
        titulo.setAlignmentX(Component.CENTER_ALIGNMENT);

        // Separador
        JSeparator sep1 = new JSeparator();
        sep1.setForeground(new Color(40, 80, 140));
        sep1.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));

        // Info do usuário
        JLabel lblUsuario = new JLabel(usuarioLogado.getUsername(), SwingConstants.CENTER);
        lblUsuario.setForeground(COR_TEXTO_SEC);
        lblUsuario.setFont(new Font("Segoe UI", Font.ITALIC, 12));
        lblUsuario.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel lblPapel = new JLabel(usuarioLogado.getPapel());
        lblPapel.setForeground(new Color(100, 200, 120));
        lblPapel.setFont(new Font("Segoe UI", Font.BOLD, 11));
        lblPapel.setAlignmentX(Component.CENTER_ALIGNMENT);

        JSeparator sep2 = new JSeparator();
        sep2.setForeground(new Color(40, 80, 140));
        sep2.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));

        // Botões de navegação
        JButton btnDash      = criarBotaoMenu("Dashboard", false);
        JButton btnCarros    = criarBotaoMenu("Carros", false);
        JButton btnFunc      = criarBotaoMenu("Funcionários", false);
        JButton btnVendas    = criarBotaoMenu("Vendas", false);
        JButton btnRelatorio = criarBotaoMenu("Relatórios", false);
        JButton btnUsuarios  = criarBotaoMenu("Usuários", false);
        JButton btnLogout    = criarBotaoMenu("Sair", true);

        // Monta o menu
        menu.add(logo);
        menu.add(Box.createVerticalStrut(4));
        menu.add(titulo);
        menu.add(Box.createVerticalStrut(10));
        menu.add(sep1);
        menu.add(Box.createVerticalStrut(8));
        menu.add(lblUsuario);
        menu.add(Box.createVerticalStrut(2));
        menu.add(lblPapel);
        menu.add(Box.createVerticalStrut(8));
        menu.add(sep2);
        menu.add(Box.createVerticalStrut(16));
        menu.add(btnDash);
        menu.add(Box.createVerticalStrut(6));
        menu.add(btnCarros);
        menu.add(Box.createVerticalStrut(6));
        menu.add(btnFunc);
        menu.add(Box.createVerticalStrut(6));
        menu.add(btnVendas);
        menu.add(Box.createVerticalStrut(6));
        menu.add(btnRelatorio);
        menu.add(Box.createVerticalStrut(6));
        menu.add(btnUsuarios);
        menu.add(Box.createVerticalGlue());
        menu.add(btnLogout);

        // Ações — obtidas depois de criar as abas (usamos index)
        // As abas são criadas em criarAbas() que é chamado após esse método,
        // por isso usamos referência indireta via campo
        btnDash.addActionListener(e -> { atualizarDashboard(); selecionarAba(0); });
        btnCarros.addActionListener(e   -> selecionarAba(1));
        btnFunc.addActionListener(e     -> selecionarAba(2));
        btnVendas.addActionListener(e   -> selecionarAba(3));
        btnRelatorio.addActionListener(e -> { if (relatorioPanel != null) relatorioPanel.atualizar(); selecionarAba(4); });
        btnUsuarios.addActionListener(e -> selecionarAba(5));
        btnLogout.addActionListener(e -> fazerLogout());

        return menu;
    }

    // Referência ao JTabbedPane para navegação do menu lateral
    private JTabbedPane abasPrincipal;

    private void selecionarAba(int i) {
        if (abasPrincipal != null) abasPrincipal.setSelectedIndex(i);
    }

    // ════════════════════════════════════════════════════════════════════
    // ABAS
    // ════════════════════════════════════════════════════════════════════
    private JTabbedPane criarAbas() {
        relatorioPanel = new RelatorioPanel(vendasDAO, funcionarioDAO, carroBD);

        abasPrincipal = new JTabbedPane();
        abasPrincipal.setBackground(COR_FUNDO);
        abasPrincipal.setForeground(COR_BRANCO);
        abasPrincipal.addTab("Dashboard", criarDashboard());
        abasPrincipal.addTab("Carros", criarPainelCarros());
        abasPrincipal.addTab("Funcionários", criarPainelFuncionarios());
        abasPrincipal.addTab("Vendas", criarPainelVendas());
        abasPrincipal.addTab("Relatórios", relatorioPanel);
        abasPrincipal.addTab("Usuários", criarPainelUsuarios());
        return abasPrincipal;
    }

    // ════════════════════════════════════════════════════════════════════
    // DASHBOARD
    // ════════════════════════════════════════════════════════════════════
    private JPanel criarDashboard() {
        JPanel dash = new JPanel(new BorderLayout(0, 20));
        dash.setBackground(COR_FUNDO);
        dash.setBorder(new EmptyBorder(20, 20, 20, 20));

        // Título de boas-vindas
        JPanel topoBemVindo = new JPanel(new BorderLayout());
        topoBemVindo.setBackground(COR_FUNDO);
        JLabel bemvindo = new JLabel("Bem-vindo, " + usuarioLogado.getUsername() + "!");
        bemvindo.setForeground(COR_BRANCO);
        bemvindo.setFont(new Font("Segoe UI", Font.BOLD, 24));
        JLabel subtitulo = new JLabel("Painel de controle da concessionária");
        subtitulo.setForeground(COR_TEXTO_SEC);
        subtitulo.setFont(FONTE_PADRAO);
        topoBemVindo.add(bemvindo,  BorderLayout.NORTH);
        topoBemVindo.add(subtitulo, BorderLayout.SOUTH);
        JPanel controlesDashboard = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        controlesDashboard.setBackground(COR_FUNDO);
        comboPeriodoDashboard = new JComboBox<>(new String[]{"Todo o período", "Últimos 3 meses", "Últimos 6 meses", "Últimos 12 meses"});
        JButton btnAtualizarDashboard = criarBotao("Atualizar", COR_BOTAO);
        comboPeriodoDashboard.setToolTipText("Filtra vendas, receita e ranking; o estoque mostra a situação atual.");
        btnAtualizarDashboard.setToolTipText("Consultar novamente os dados do banco.");
        btnAtualizarDashboard.addActionListener(e -> atualizarDashboard());
        comboPeriodoDashboard.addActionListener(e -> atualizarDashboard());
        controlesDashboard.add(new JLabel("Período:"));
        controlesDashboard.add(comboPeriodoDashboard);
        controlesDashboard.add(btnAtualizarDashboard);
        topoBemVindo.add(controlesDashboard, BorderLayout.EAST);
        dash.add(topoBemVindo, BorderLayout.NORTH);

        JPanel conteudo = new JPanel(new BorderLayout(0, 12));
        conteudo.setBackground(COR_FUNDO);

        // Grid de cards
        JPanel cards = new JPanel(new GridLayout(2, 3, 16, 12));
        cards.setBackground(COR_FUNDO);
        cards.setPreferredSize(new Dimension(900, 182));
        cards.setMaximumSize(new Dimension(Integer.MAX_VALUE, 182));

        lblTotalCarros  = new JLabel("—");
        lblDisponiveis  = new JLabel("—");
        lblTotalFunc    = new JLabel("—");
        lblTotalVendas  = new JLabel("—");
        lblReceitaTotal = new JLabel("—");
        lblTaxaConversao = new JLabel("—");
        lblVariacaoVendas = new JLabel(" ");
        lblVariacaoReceita = new JLabel(" ");

        cards.add(criarCardDash("Total de Carros",   lblTotalCarros,   COR_BOTAO));
        cards.add(criarCardDash("Disponíveis",       lblDisponiveis,   new Color(39, 174, 96)));
        cards.add(criarCardDash("Funcionários",      lblTotalFunc,     new Color(142, 68, 173)));
        cards.add(criarCardDash("Vendas no período", lblTotalVendas,   new Color(211, 84, 0), lblVariacaoVendas));
        cards.add(criarCardDash("Receita no período", lblReceitaTotal,  new Color(22, 160, 133), lblVariacaoReceita));
        cards.add(criarCardDash("% do Estoque Vendido", lblTaxaConversao, new Color(241, 196, 15)));

        JPanel graficos = new JPanel(new GridLayout(1, 3, 12, 0));
        graficos.setBackground(COR_FUNDO);
        graficos.setPreferredSize(new Dimension(900, 290));
        graficos.setMinimumSize(new Dimension(0, 250));
        graficoReceitaVendas = new DashboardCombinedChart();
        graficoEstoque = new DashboardDonutChart();
        graficoVendedores = new DashboardSellerChart();
        graficoReceitaVendas.setOnMonthClick(this::mostrarVendasDoMes);
        graficoEstoque.setOnStatusClick(this::mostrarCarrosDoStatus);
        graficoVendedores.setOnSellerClick(this::mostrarVendasDoVendedor);
        graficos.add(graficoReceitaVendas);
        graficos.add(graficoEstoque);
        graficos.add(graficoVendedores);
        conteudo.add(cards, BorderLayout.NORTH);
        conteudo.add(graficos, BorderLayout.CENTER);
        dash.add(conteudo, BorderLayout.CENTER);

        // Rodapé do dashboard
        JPanel rodape = new JPanel(new BorderLayout(0, 3));
        rodape.setBackground(COR_FUNDO);
        JPanel infoRodape = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        infoRodape.setBackground(COR_FUNDO);
        lblDashPeriodo = new JLabel("Período: —");
        lblDashPeriodo.setForeground(COR_TEXTO_SEC);
        lblDashPeriodo.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        infoRodape.add(lblDashPeriodo);
        rodape.add(infoRodape, BorderLayout.NORTH);
        lblDashAviso = new JLabel(" ");
        lblDashAviso.setForeground(new Color(255, 204, 102));
        lblDashAviso.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        rodape.add(lblDashAviso, BorderLayout.SOUTH);
        dash.add(rodape, BorderLayout.SOUTH);
        return dash;
    }

    private JPanel criarCardDash(String titulo, JLabel valorLabel, Color cor) {
        return criarCardDash(titulo, valorLabel, cor, null);
    }

    private JPanel criarCardDash(String titulo, JLabel valorLabel, Color cor, JLabel variacao) {
        JPanel card = new JPanel();
        card.setPreferredSize(new Dimension(0, 86));
        card.setBackground(new Color(18, 35, 70));
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 3, 0, 0, cor),
                new EmptyBorder(9, 12, 9, 12)));
        card.setLayout(new BorderLayout(0, 1));

        JLabel lblTitulo = new JLabel(titulo);
        lblTitulo.setForeground(COR_TEXTO_SEC);
        lblTitulo.setFont(new Font("Segoe UI", Font.PLAIN, 11));

        valorLabel.setForeground(COR_BRANCO);
        valorLabel.setFont(new Font("Segoe UI", Font.BOLD, 20));

        card.add(lblTitulo, BorderLayout.NORTH);
        card.add(valorLabel, BorderLayout.CENTER);
        if (variacao != null) {
            variacao.setFont(new Font("Segoe UI", Font.PLAIN, 9));
            variacao.setForeground(COR_TEXTO_SEC);
            card.add(variacao, BorderLayout.SOUTH);
        }
        return card;
    }

    private void atualizarDashboard() {
        try {
            DashboardService.Periodo periodo = DashboardService.Periodo.porRotulo(
                    String.valueOf(comboPeriodoDashboard.getSelectedItem()));
            DashboardService.Resultado resultado = dashboardService.carregar(periodo);
            ultimoDashboard = resultado;
            lblTotalCarros.setText(String.valueOf(resultado.totalCarros()));
            lblDisponiveis.setText(String.valueOf(resultado.disponiveis()));
            lblTotalFunc.setText(String.valueOf(resultado.totalFuncionarios()));
            lblTotalVendas.setText(String.valueOf(resultado.vendasRealizadas()));
            lblReceitaTotal.setText(NumberFormat.getCurrencyInstance(new Locale("pt", "BR"))
                    .format(resultado.receitaTotal()));
            lblTaxaConversao.setText(String.format(new Locale("pt", "BR"), "%.1f%%", resultado.percentualEstoqueVendido()));
            configurarVariacao(lblVariacaoVendas, resultado.variacaoVendas());
            configurarVariacao(lblVariacaoReceita, resultado.variacaoReceita());
            graficoReceitaVendas.setResultado(resultado);
            graficoEstoque.setResultado(resultado);
            graficoVendedores.setResultado(resultado);

            String atualizado = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
            lblDashPeriodo.setText("Vendas: " + resultado.periodo().rotulo() + " (" + resultado.periodoCoberto()
                    + ")  •  Meses sem vendas omitidos  •  Estoque atual  •  Atualizado: " + atualizado);
            lblDashAviso.setText(resultado.avisos().isEmpty() ? " " : "⚠ " + resultado.avisos().get(0));
            lblDashAviso.setToolTipText(resultado.avisos().isEmpty() ? null
                    : "<html>" + String.join("<br>", resultado.avisos()) + "</html>");
        } catch (Exception ex) {
            System.err.println("Falha ao carregar o dashboard: " + ex.getMessage());
            lblTotalCarros.setText("—"); lblDisponiveis.setText("—"); lblTotalFunc.setText("—");
            lblTotalVendas.setText("—"); lblReceitaTotal.setText("—"); lblTaxaConversao.setText("—");
            lblVariacaoVendas.setText(" "); lblVariacaoReceita.setText(" ");
            ultimoDashboard = null;
            lblDashPeriodo.setText("Dados indisponíveis");
            lblDashAviso.setText("Não foi possível carregar os indicadores. Verifique a conexão com o banco.");
            graficoReceitaVendas.setErro("Não foi possível carregar os dados.");
            graficoEstoque.setErro("Não foi possível carregar os dados.");
            graficoVendedores.setErro("Não foi possível carregar os dados.");
        }
    }

    private void configurarVariacao(JLabel label, String texto) {
        label.setText(texto);
        label.setForeground(texto.startsWith("▲") ? new Color(80, 210, 145)
                : texto.startsWith("▼") ? new Color(255, 125, 125) : COR_TEXTO_SEC);
    }

    private void mostrarVendasDoMes(YearMonth mes) {
        if (ultimoDashboard == null) return;
        List<DashboardService.VendaDetalhe> vendas = ultimoDashboard.vendasDetalhe().stream()
                .filter(v -> YearMonth.from(v.data()).equals(mes)).toList();
        abrirTabelaVendas("Vendas de " + mes.format(DateTimeFormatter.ofPattern("MMMM 'de' yyyy", new Locale("pt", "BR"))), vendas);
    }

    private void mostrarVendasDoVendedor(String vendedor) {
        if (ultimoDashboard == null) return;
        List<DashboardService.VendaDetalhe> vendas = ultimoDashboard.vendasDetalhe().stream()
                .filter(v -> v.vendedor().equals(vendedor)).toList();
        abrirTabelaVendas("Vendas de " + vendedor, vendas);
    }

    private void abrirTabelaVendas(String titulo, List<DashboardService.VendaDetalhe> vendas) {
        Object[][] linhas = vendas.stream().map(v -> new Object[]{v.data().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
                v.carro(), v.vendedor(), NumberFormat.getCurrencyInstance(new Locale("pt", "BR")).format(v.valor())})
                .toArray(Object[][]::new);
        abrirTabelaDetalhe(titulo, new Object[]{"Data", "Veículo", "Vendedor", "Valor"}, linhas);
    }

    private void mostrarCarrosDoStatus(String status) {
        if (ultimoDashboard == null) return;
        Object[][] linhas = ultimoDashboard.carros().stream()
                .filter(c -> status.equalsIgnoreCase(c.status()))
                .map(c -> new Object[]{c.id(), c.nome(), c.modelo(), c.ano()})
                .toArray(Object[][]::new);
        abrirTabelaDetalhe("Veículos " + status.toLowerCase(new Locale("pt", "BR")),
                new Object[]{"ID", "Marca/nome", "Modelo", "Ano"}, linhas);
    }

    private void abrirTabelaDetalhe(String titulo, Object[] colunas, Object[][] linhas) {
        if (linhas.length == 0) {
            JOptionPane.showMessageDialog(this, "Não há registros para este item.", titulo, JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        DefaultTableModel model = new DefaultTableModel(linhas, colunas) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
        };
        JTable tabela = new JTable(model);
        estilizarTabela(tabela);
        tabela.setFillsViewportHeight(true);
        JDialog dialogo = new JDialog(this, titulo, true);
        dialogo.setLayout(new BorderLayout());
        dialogo.add(new JScrollPane(tabela), BorderLayout.CENTER);
        dialogo.setSize(700, 360);
        dialogo.setLocationRelativeTo(this);
        dialogo.setVisible(true);
    }

    // ════════════════════════════════════════════════════════════════════
    // PAINEL CARROS
    // ════════════════════════════════════════════════════════════════════
    private JPanel criarPainelCarros() {
        JPanel painel = new JPanel(new BorderLayout(0, 0));
        painel.setBackground(COR_FUNDO);

        JTable tabela = new JTable(dtmCarros);
        estilizarTabela(tabela);
        configurarColunaMoeda(tabela, 4);

        // MELHORIA: JToolBar no lugar de JPanel com botões soltos
        JToolBar toolbar = criarToolbar();
        JButton listar  = criarBotao("Listar", null);
        JButton add     = criarBotao("Adicionar", new Color(39, 174, 96));
        JButton editar  = criarBotao("Editar", COR_BOTAO);
        JButton excluir = criarBotao("Excluir", COR_DANGER);
        JTextField busca = criarCampoBusca("Buscar carro…");

        busca.addActionListener(e -> filtrarTabela(dtmCarros, busca.getText(), carroDAO.loadAll(), 1));

        toolbar.add(listar); toolbar.add(add); toolbar.add(editar); toolbar.add(excluir);
        toolbar.addSeparator();
        toolbar.add(new JLabel("  Buscar: "));
        toolbar.add(busca);

        listar.addActionListener(e  -> preencherCarros(carroDAO.loadAll()));
        add.addActionListener(e     -> adicionarCarro());
        editar.addActionListener(e  -> editarCarro(tabela));
        excluir.addActionListener(e -> excluirCarro(tabela));

        painel.add(toolbar, BorderLayout.NORTH);
        painel.add(new JScrollPane(tabela), BorderLayout.CENTER);
        return painel;
    }

    // ════════════════════════════════════════════════════════════════════
    // PAINEL FUNCIONÁRIOS
    // ════════════════════════════════════════════════════════════════════
    private JPanel criarPainelFuncionarios() {
        JPanel painel = new JPanel(new BorderLayout());
        painel.setBackground(COR_FUNDO);

        JTable tabela = new JTable(dtmFunc);
        estilizarTabela(tabela);
        configurarColunaMoeda(tabela, 2);

        JToolBar toolbar = criarToolbar();
        JButton listar    = criarBotao("Listar", null);
        JButton registrar = criarBotao("Registrar", new Color(39, 174, 96));
        JButton editar    = criarBotao("Editar", COR_BOTAO);
        JButton excluir   = criarBotao("Excluir", COR_DANGER);

        toolbar.add(listar); toolbar.add(registrar); toolbar.add(editar); toolbar.add(excluir);

        listar.addActionListener(e    -> preencherFuncionarios(funcionarioDAO.loadAll()));
        registrar.addActionListener(e -> registrarFuncionario());
        editar.addActionListener(e    -> editarFuncionario(tabela));
        excluir.addActionListener(e   -> excluirFuncionario(tabela));

        painel.add(toolbar, BorderLayout.NORTH);
        painel.add(new JScrollPane(tabela), BorderLayout.CENTER);
        return painel;
    }

    // ════════════════════════════════════════════════════════════════════
    // PAINEL VENDAS
    // ════════════════════════════════════════════════════════════════════
    private JPanel criarPainelVendas() {
        JPanel painel = new JPanel(new BorderLayout());
        painel.setBackground(COR_FUNDO);

        JTable tabela = new JTable(dtmVendas);
        estilizarTabela(tabela);
        configurarColunaMoeda(tabela, 3);
        configurarColunaMoeda(tabela, 4);

        JToolBar toolbar = criarToolbar();
        JButton listar  = criarBotao("Listar vendas", null);
        JButton add     = criarBotao("Registrar", new Color(39, 174, 96));
        JButton excluir = criarBotao("Excluir", COR_DANGER);

        toolbar.add(listar); toolbar.add(add); toolbar.add(excluir);

        listar.addActionListener(e  -> preencherVendas(vendasDAO.listarVendas()));
        add.addActionListener(e     -> adicionarVenda());
        excluir.addActionListener(e -> excluirVenda(tabela));

        painel.add(toolbar, BorderLayout.NORTH);
        painel.add(new JScrollPane(tabela), BorderLayout.CENTER);
        return painel;
    }

    // ════════════════════════════════════════════════════════════════════
    // PAINEL USUÁRIOS
    // ════════════════════════════════════════════════════════════════════
    private JPanel criarPainelUsuarios() {
        JPanel painel = new JPanel(new BorderLayout());
        painel.setBackground(COR_FUNDO);

        JTable tabela = new JTable(dtmUsuarios);
        estilizarTabela(tabela);

        JToolBar toolbar = criarToolbar();
        JButton listar    = criarBotao("Listar", null);
        JButton adicionar = criarBotao("Adicionar", new Color(39, 174, 96));
        JButton editar    = criarBotao("Editar", COR_BOTAO);
        JButton excluir   = criarBotao("Excluir", COR_DANGER);

        toolbar.add(listar); toolbar.add(adicionar); toolbar.add(editar); toolbar.add(excluir);

        listar.addActionListener(e    -> preencherUsuarios(usuarioDAO.listarTodos()));
        adicionar.addActionListener(e -> adicionarUsuario());
        editar.addActionListener(e    -> editarUsuario(tabela));
        excluir.addActionListener(e   -> excluirUsuario(tabela));

        painel.add(toolbar, BorderLayout.NORTH);
        painel.add(new JScrollPane(tabela), BorderLayout.CENTER);
        return painel;
    }

    // ════════════════════════════════════════════════════════════════════
    // AÇÕES — CARROS
    // ════════════════════════════════════════════════════════════════════
    private void adicionarCarro() {
        abrirFormularioCarro(null);
    }

    private void editarCarro(JTable tabela) {
        int linha = tabela.getSelectedRow();
        if (linha < 0) { aviso("Selecione um carro para editar."); return; }
        int id = (int) dtmCarros.getValueAt(linha, 0);
        Carro atual = carroDAO.loadAll().stream()
                .filter(c -> c.getId() == id)
                .findFirst().orElse(null);
        if (atual == null) { aviso("Carro não encontrado."); return; }
        abrirFormularioCarro(atual);
    }

    private void abrirFormularioCarro(Carro atual) {
        JTextField txtNome = new JTextField(atual != null ? atual.getNome() : "");
        JTextField txtModelo = new JTextField(atual != null ? atual.getModelo() : "");
        JTextField txtAno = new JTextField(atual != null ? String.valueOf(atual.getAno()) : "");
        JTextField txtPreco = new JTextField(atual != null ? String.valueOf(atual.getPreco()) : "");
        JComboBox<String> comboNivel = new JComboBox<>(new String[]{"Basico", "Medio", "Luxo"});
        JLabel lblStatus = new JLabel(atual == null ? "Disponível (definido automaticamente)"
                : atual.getStatus() + " (gerenciado pelo fluxo de vendas)");

        if (atual != null) {
            comboNivel.setSelectedItem(atual.getNivel());
        }

        JPanel form = criarGridFormulario();
        adicionarLinhaFormulario(form, "Nome:", txtNome);
        adicionarLinhaFormulario(form, "Modelo:", txtModelo);
        adicionarLinhaFormulario(form, "Ano:", txtAno);
        adicionarLinhaFormulario(form, "Preço:", txtPreco);
        adicionarLinhaFormulario(form, "Status:", lblStatus);
        adicionarLinhaFormulario(form, "Nível:", comboNivel);

        abrirDialogoFormulario(atual == null ? "Adicionar carro" : "Editar carro", form, dialog -> {
            String nome = txtNome.getText().trim();
            String modelo = txtModelo.getText().trim();
            if (nome.isBlank() || modelo.isBlank()) {
                aviso("Preencha nome e modelo.");
                return;
            }

            try {
                int ano = Integer.parseInt(txtAno.getText().trim());
                double preco = parseNumeroBr(txtPreco.getText());
                String nivel = (String) comboNivel.getSelectedItem();

                if (atual == null) {
                    carroDAO.save(new Carro(0, nome, modelo, ano, preco, "Disponível", nivel));
                    dialog.dispose();
                    sucesso("Carro salvo com sucesso!");
                } else {
                    carroDAO.update(new Carro(atual.getId(), nome, modelo, ano, preco, atual.getStatus(), nivel));
                    dialog.dispose();
                    sucesso("Carro editado com sucesso!");
                }
                preencherCarros(carroDAO.loadAll());
                atualizarDashboard();
            } catch (NumberFormatException e) {
                erro("Ano ou preço inválido.");
            } catch (Exception e) {
                erro("Erro ao salvar: " + e.getMessage());
            }
        });
    }

    private void excluirCarro(JTable tabela) {
        int linha = tabela.getSelectedRow();
        if (linha < 0) { aviso("Selecione um carro para excluir."); return; }
        if (!confirmar("Excluir o carro selecionado?")) return;
        try {
            carroDAO.deleteById((int) dtmCarros.getValueAt(linha, 0));
            preencherCarros(carroDAO.loadAll());
            sucesso("Carro excluído.");
        } catch (Exception e) {
            erro("Erro: " + e.getMessage());
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // AÇÕES — FUNCIONÁRIOS
    // ════════════════════════════════════════════════════════════════════
    private void registrarFuncionario() {
        abrirFormularioFuncionario(null);
    }

    private void editarFuncionario(JTable tabela) {
        int linha = tabela.getSelectedRow();
        if (linha < 0) { aviso("Selecione um funcionário para editar."); return; }
        int id = (int) dtmFunc.getValueAt(linha, 0);
        Funcionario atual = funcionarioDAO.loadAll().stream()
                .filter(f -> f.getId() == id)
                .findFirst().orElse(null);
        if (atual == null) { aviso("Funcionário não encontrado."); return; }
        abrirFormularioFuncionario(atual);
    }

    private void abrirFormularioFuncionario(Funcionario atual) {
        JTextField txtNome = new JTextField(atual != null ? atual.getNome() : "");
        JTextField txtSalario = new JTextField(atual != null ? String.valueOf(atual.getSalario()) : "");

        JPanel form = criarGridFormulario();
        adicionarLinhaFormulario(form, "Nome:", txtNome);
        adicionarLinhaFormulario(form, "Salário:", txtSalario);

        abrirDialogoFormulario(atual == null ? "Registrar funcionário" : "Editar funcionário", form, dialog -> {
            String nome = txtNome.getText().trim();
            if (nome.isBlank()) {
                aviso("Informe o nome do funcionário.");
                return;
            }
            try {
                double salario = parseNumeroBr(txtSalario.getText());
                if (atual == null) {
                    funcionarioDAO.save(new Funcionario(0, nome, salario));
                    dialog.dispose();
                    sucesso("Funcionário registrado!");
                } else {
                    funcionarioDAO.update(new Funcionario(atual.getId(), nome, salario));
                    dialog.dispose();
                    sucesso("Funcionário editado!");
                }
                preencherFuncionarios(funcionarioDAO.loadAll());
                atualizarDashboard();
            } catch (Exception e) {
                erro("Erro: " + e.getMessage());
            }
        });
    }

    private void excluirFuncionario(JTable tabela) {
        int linha = tabela.getSelectedRow();
        if (linha < 0) { aviso("Selecione um funcionário para excluir."); return; }
        if (!confirmar("Excluir o funcionário selecionado?")) return;
        try {
            funcionarioDAO.deleteById((int) dtmFunc.getValueAt(linha, 0));
            preencherFuncionarios(funcionarioDAO.loadAll());
            sucesso("Funcionário excluído.");
        } catch (Exception e) {
            erro("Erro: " + e.getMessage());
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // AÇÕES — VENDAS
    // ════════════════════════════════════════════════════════════════════
    private void adicionarVenda() {
        List<Carro>       carros = carroBD.loadDisponiveis();
        List<Funcionario> funcs  = funcionarioDAO.loadAll();

        if (carros.isEmpty()) { aviso("Não há carros disponíveis para venda."); return; }
        if (funcs.isEmpty())  { aviso("Cadastre pelo menos um funcionário antes de registrar uma venda."); return; }

        JComboBox<Carro>       comboCarro = new JComboBox<>(carros.toArray(new Carro[0]));
        JComboBox<Funcionario> comboFunc  = new JComboBox<>(funcs.toArray(new Funcionario[0]));
        JLabel lblComissao = new JLabel("Comissão: R$ —");
        lblComissao.setFont(new Font("Segoe UI", Font.BOLD, 13));
        lblComissao.setForeground(new Color(130, 235, 170));

        comboCarro.addActionListener(e -> {
            Carro c = (Carro) comboCarro.getSelectedItem();
            if (c != null) {
                double com = ComissaoService.calcularComissao(c.getPreco(), c.getNivel());
                lblComissao.setText(String.format("Comissão: R$ %.2f  (%s — %.0f%%)",
                        com, c.getNivel(), com / c.getPreco() * 100));
            }
        });
        comboCarro.setSelectedIndex(0);

        JTextField txtData = new JTextField(LocalDate.now().toString());

        JPanel form = criarGridFormulario();
        adicionarLinhaFormulario(form, "Carro disponível:", comboCarro);
        adicionarLinhaFormulario(form, "Vendedor:", comboFunc);
        adicionarLinhaFormulario(form, "Data (yyyy-MM-dd):", txtData);
        adicionarLinhaFormulario(form, "Comissão estimada:", lblComissao);

        abrirDialogoFormulario("Registrar venda", form, dialog -> {
            try {
                Carro       carro   = (Carro) comboCarro.getSelectedItem();
                Funcionario func    = (Funcionario) comboFunc.getSelectedItem();
                double comissao     = ComissaoService.calcularComissao(carro.getPreco(), carro.getNivel());
                LocalDate data      = LocalDate.parse(txtData.getText().trim());

                vendasDAO.registrarVenda(new Vendas(0, carro.getId(), func.getId(), carro.getPreco(), comissao, data));
                preencherVendas(vendasDAO.listarVendas());
                atualizarDashboard();
                dialog.dispose();
                sucesso(String.format("Venda registrada!\nCarro: %s\nVendedor: %s\nComissão: R$ %.2f",
                        carro.getNome(), func.getNome(), comissao));

            } catch (Exception e) {
                erro("Erro ao registrar venda: " + e.getMessage());
            }
        });
    }

    private void excluirVenda(JTable tabela) {
        int linha = tabela.getSelectedRow();
        if (linha < 0) { aviso("Selecione uma venda para excluir."); return; }
        if (!confirmar("Excluir a venda selecionada? O carro voltará a ficar Disponível se não houver outra venda ativa.")) return;
        try {
            int idVenda = (int) dtmVendas.getValueAt(linha, 0);
            vendasDAO.deleteById(idVenda); // remove a venda e atualiza o estoque na mesma transação

            preencherVendas(vendasDAO.listarVendas());
            atualizarDashboard();
            sucesso("Venda excluída. O estoque foi atualizado junto com a operação.");
        } catch (Exception e) {
            erro("Erro: " + e.getMessage());
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // AÇÕES — USUÁRIOS
    // ════════════════════════════════════════════════════════════════════
    private void adicionarUsuario() {
        abrirFormularioUsuario(null);
    }

    private void editarUsuario(JTable tabela) {
        int linha = tabela.getSelectedRow();
        if (linha < 0) { aviso("Selecione um usuário para editar."); return; }

        int id = (int) dtmUsuarios.getValueAt(linha, 0);
        Usuario atual = usuarioDAO.listarTodos().stream()
                .filter(u -> u.getId() == id).findFirst().orElse(null);
        if (atual == null) { aviso("Usuário não encontrado."); return; }
        abrirFormularioUsuario(atual);
    }

    private void abrirFormularioUsuario(Usuario atual) {
        boolean edicao = atual != null;
        JTextField txtUsername = new JTextField(edicao ? atual.getUsername() : "");
        JPasswordField pfSenha = new JPasswordField();
        JPasswordField pfConfirmar = new JPasswordField();
        JComboBox<String> comboPapel = new JComboBox<>(new String[]{"GERENTE", "VENDEDOR"});
        JComboBox<Object> comboFunc = criarComboFuncionarios(edicao ? atual.getFuncionarioId() : null);

        if (edicao) comboPapel.setSelectedItem(atual.getPapel());

        JPanel form = criarGridFormulario();
        adicionarLinhaFormulario(form, "Username:", txtUsername);
        adicionarLinhaFormulario(form, edicao ? "Nova senha (opcional):" : "Senha:", pfSenha);
        adicionarLinhaFormulario(form, edicao ? "Confirmar nova senha:" : "Confirmar senha:", pfConfirmar);
        adicionarLinhaFormulario(form, "Papel:", comboPapel);
        adicionarLinhaFormulario(form, "Funcionário (vendedor):", comboFunc);

        Runnable syncCombo = () -> {
            boolean vendedor = "VENDEDOR".equals(comboPapel.getSelectedItem());
            comboFunc.setEnabled(vendedor);
            if (!vendedor) comboFunc.setSelectedIndex(0);
        };
        comboPapel.addActionListener(e -> syncCombo.run());
        syncCombo.run();

        abrirDialogoFormulario(edicao ? "Editar usuário" : "Adicionar usuário", form, dialog -> {
            String username = txtUsername.getText().trim();
            String senha = new String(pfSenha.getPassword());
            String confirmarSenha = new String(pfConfirmar.getPassword());
            String papel = (String) comboPapel.getSelectedItem();

            if (username.isEmpty()) {
                aviso("Preencha o usuário.");
                return;
            }
            if (!edicao && senha.isEmpty()) {
                aviso("Preencha a senha.");
                return;
            }
            if (!senha.isEmpty() || !confirmarSenha.isEmpty()) {
                if (!senha.equals(confirmarSenha)) {
                    aviso("As senhas não coincidem.");
                    return;
                }
                if (senha.length() < 6) {
                    aviso("A senha deve ter pelo menos 6 caracteres.");
                    return;
                }
            }

            Integer funcionarioId = null;
            if ("VENDEDOR".equals(papel)) {
                funcionarioId = extrairFuncionarioId(comboFunc);
                if (funcionarioId == null) {
                    aviso("Vendedor precisa estar vinculado a um funcionário cadastrado.");
                    return;
                }
            }

            try {
                if (edicao) {
                    usuarioDAO.update(new Usuario(atual.getId(), username, papel, funcionarioId), senha);
                    dialog.dispose();
                    sucesso("Usuário editado!");
                } else {
                    usuarioDAO.criarUsuario(username, senha, papel, funcionarioId);
                    dialog.dispose();
                    sucesso("Usuário adicionado com sucesso!");
                }
                preencherUsuarios(usuarioDAO.listarTodos());
            } catch (Exception e) {
                erro("Erro: " + e.getMessage());
            }
        });
    }

    private void excluirUsuario(JTable tabela) {
        int linha = tabela.getSelectedRow();
        if (linha < 0) { aviso("Selecione um usuário para excluir."); return; }
        int id = (int) dtmUsuarios.getValueAt(linha, 0);
        if (id == usuarioLogado.getId()) { aviso("Você não pode excluir o seu próprio usuário."); return; }
        if (!confirmar("Excluir o usuário selecionado?")) return;
        try {
            usuarioDAO.deleteById(id);
            preencherUsuarios(usuarioDAO.listarTodos());
            sucesso("Usuário excluído.");
        } catch (Exception e) {
            erro("Erro: " + e.getMessage());
        }
    }

    /** Combo com "— nenhum —" + funcionários; pré-seleciona o id informado. */
    private JComboBox<Object> criarComboFuncionarios(Integer selecionadoId) {
        JComboBox<Object> combo = new JComboBox<>();
        combo.addItem("— nenhum —");
        int idxSelecionado = 0;
        List<Funcionario> funcs = funcionarioDAO.loadAll();
        for (int i = 0; i < funcs.size(); i++) {
            Funcionario f = funcs.get(i);
            combo.addItem(f);
            if (selecionadoId != null && f.getId() == selecionadoId) {
                idxSelecionado = i + 1;
            }
        }
        combo.setSelectedIndex(idxSelecionado);
        return combo;
    }

    private Integer extrairFuncionarioId(JComboBox<Object> combo) {
        Object sel = combo.getSelectedItem();
        if (sel instanceof Funcionario f) return f.getId();
        return null;
    }

    // ════════════════════════════════════════════════════════════════════
    // LOGOUT
    // ════════════════════════════════════════════════════════════════════
    private void fazerLogout() {
        dispose();
        SwingUtilities.invokeLater(() -> new LoginFrame().setVisible(true));
    }

    // ════════════════════════════════════════════════════════════════════
    // HELPERS — TABELAS
    // ════════════════════════════════════════════════════════════════════
    private void preencherCarros(List<Carro> carros) {
        dtmCarros.setRowCount(0);
        for (Carro c : carros)
            dtmCarros.addRow(new Object[]{c.getId(), c.getNome(), c.getModelo(), c.getAno(), c.getPreco(), c.getStatus(), c.getNivel()});
    }

    private void preencherFuncionarios(List<Funcionario> lista) {
        dtmFunc.setRowCount(0);
        for (Funcionario f : lista)
            dtmFunc.addRow(new Object[]{f.getId(), f.getNome(), f.getSalario()});
    }

    private void preencherUsuarios(List<Usuario> lista) {
        List<Funcionario> funcs = funcionarioDAO.loadAll();
        dtmUsuarios.setRowCount(0);
        for (Usuario u : lista) {
            String vinculo = "—";
            if (u.getFuncionarioId() != null) {
                vinculo = funcs.stream()
                        .filter(f -> f.getId() == u.getFuncionarioId())
                        .map(f -> "[" + f.getId() + "] " + f.getNome())
                        .findFirst()
                        .orElse("ID " + u.getFuncionarioId());
            }
            dtmUsuarios.addRow(new Object[]{u.getId(), u.getUsername(), u.getPapel(), vinculo});
        }
    }

    /** MELHORIA: exibe nome do carro e do vendedor — busca nas listas em memória. */
    private void preencherVendas(List<Vendas> lista) {
        List<Carro>       carros = carroDAO.loadAll();
        List<Funcionario> funcs  = funcionarioDAO.loadAll();
        dtmVendas.setRowCount(0);
        for (Vendas v : lista) {
            String nomeCarro   = carros.stream().filter(c -> c.getId() == v.getIdCarro())
                    .map(c -> c.getNome() + " " + c.getModelo()).findFirst().orElse("ID " + v.getIdCarro());
            String nomeVendedor = funcs.stream().filter(f -> f.getId() == v.getIdFuncionario())
                    .map(Funcionario::getNome).findFirst().orElse("ID " + v.getIdFuncionario());
            dtmVendas.addRow(new Object[]{v.getIdVenda(), nomeCarro, nomeVendedor,
                    v.getValorvenda(), v.getComissao(), v.getDataVenda()});
        }
    }

    /** Filtra a tabela de carros pelo texto digitado na busca. */
    private void filtrarTabela(DefaultTableModel model, String texto, List<Carro> todos, int colunaTexto) {
        if (texto == null || texto.isBlank()) { preencherCarros(todos); return; }
        preencherCarros(todos);
        String lower = texto.toLowerCase();
        for (int i = model.getRowCount() - 1; i >= 0; i--) {
            String val = String.valueOf(model.getValueAt(i, colunaTexto)).toLowerCase();
            if (!val.contains(lower)) model.removeRow(i);
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // HELPERS — UI
    // ════════════════════════════════════════════════════════════════════
    private JButton criarBotaoMenu(String texto, boolean perigo) {
        JButton b = new JButton(texto);
        b.setBackground(perigo ? COR_DANGER : COR_BOTAO);
        b.setForeground(COR_BRANCO);
        b.setFocusPainted(false);
        b.setBorder(new EmptyBorder(8, 10, 8, 10));
        b.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        b.setAlignmentX(Component.CENTER_ALIGNMENT);
        b.setHorizontalAlignment(SwingConstants.CENTER);
        return b;
    }

    private JButton criarBotao(String texto, Color cor) {
        JButton b = new JButton(texto);
        b.setBackground(cor != null ? cor : COR_BOTAO);
        b.setForeground(COR_BRANCO);
        b.setFocusPainted(false);
        b.setBorder(new EmptyBorder(5, 12, 5, 12));
        return b;
    }

    private JToolBar criarToolbar() {
        JToolBar tb = new JToolBar();
        tb.setBackground(new Color(15, 30, 60));
        tb.setFloatable(false);
        tb.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(30, 60, 120)),
                new EmptyBorder(6, 8, 6, 8)));
        return tb;
    }

    private JPanel criarGridFormulario() {
        JPanel form = new JPanel(new GridBagLayout());
        form.setBackground(new Color(24, 38, 66));
        form.setBorder(new EmptyBorder(16, 18, 16, 18));
        form.putClientProperty("form-row", 0);
        return form;
    }

    private void adicionarLinhaFormulario(JPanel form, String rotulo, JComponent campo) {
        int row = (int) form.getClientProperty("form-row");

        JLabel lbl = new JLabel(rotulo);
        GridBagConstraints gcLabel = new GridBagConstraints();
        gcLabel.gridx = 0;
        gcLabel.gridy = row;
        gcLabel.insets = new Insets(5, 4, 5, 14);
        gcLabel.anchor = GridBagConstraints.EAST;
        gcLabel.fill = GridBagConstraints.NONE;
        gcLabel.weightx = 0;

        GridBagConstraints gcCampo = new GridBagConstraints();
        gcCampo.gridx = 1;
        gcCampo.gridy = row;
        gcCampo.insets = new Insets(5, 0, 5, 0);
        gcCampo.anchor = GridBagConstraints.WEST;
        gcCampo.fill = GridBagConstraints.HORIZONTAL;
        gcCampo.weightx = 1;

        lbl.setPreferredSize(new Dimension(180, 28));
        campo.setPreferredSize(new Dimension(320, 36));
        campo.setMinimumSize(new Dimension(220, 36));

        form.add(lbl, gcLabel);
        form.add(campo, gcCampo);
        form.putClientProperty("form-row", row + 1);
    }

    private void abrirDialogoFormulario(String titulo, JPanel form, java.util.function.Consumer<JDialog> onSalvar) {
        estilizarFormulario(form);

        JDialog dialog = new JDialog(this, titulo, true);
        dialog.setLayout(new BorderLayout(0, 10));
        dialog.getContentPane().setBackground(new Color(12, 20, 40));

        JPanel acoes = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        acoes.setBackground(new Color(12, 20, 40));
        JButton btnCancelar = criarBotao("Cancelar", COR_DANGER);
        JButton btnSalvar = criarBotao("Salvar", new Color(39, 174, 96));

        btnCancelar.addActionListener(e -> dialog.dispose());
        btnSalvar.addActionListener(e -> onSalvar.accept(dialog));

        acoes.add(btnCancelar);
        acoes.add(btnSalvar);

        dialog.add(form, BorderLayout.CENTER);
        dialog.add(acoes, BorderLayout.SOUTH);
        dialog.pack();
        dialog.setMinimumSize(new Dimension(620, 360));
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    private void estilizarFormulario(Container root) {
        for (Component c : root.getComponents()) {
            if (c instanceof JLabel lbl) {
                lbl.setForeground(new Color(200, 220, 245));
                lbl.setFont(new Font("Segoe UI", Font.PLAIN, 13));
            } else if (c instanceof JTextField tf) {
                tf.setBackground(new Color(241, 246, 255));
                tf.setForeground(new Color(18, 30, 55));
                tf.setCaretColor(new Color(18, 30, 55));
                tf.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(new Color(120, 150, 195)),
                        new EmptyBorder(7, 9, 7, 9)));
                tf.setPreferredSize(new Dimension(320, 36));
            } else if (c instanceof JComboBox<?> combo) {
                combo.setBackground(new Color(241, 246, 255));
                combo.setForeground(new Color(18, 30, 55));
                combo.setFont(new Font("Segoe UI", Font.PLAIN, 13));
                combo.setBorder(BorderFactory.createLineBorder(new Color(120, 150, 195)));
                combo.setPreferredSize(new Dimension(320, 36));
            }

            if (c instanceof Container child) estilizarFormulario(child);
        }
    }

    private JTextField criarCampoBusca(String placeholder) {
        JTextField tf = new JTextField(18);
        tf.setBackground(new Color(25, 50, 95));
        tf.setForeground(COR_BRANCO);
        tf.setCaretColor(COR_BRANCO);
        tf.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(40, 90, 160)),
                new EmptyBorder(3, 6, 3, 6)));
        tf.setToolTipText(placeholder);
        return tf;
    }

    private JPanel criarRodape() {
        JPanel rodape = new JPanel(new BorderLayout());
        rodape.setBackground(new Color(8, 15, 30));
        rodape.setBorder(new EmptyBorder(4, 12, 4, 12));
        JLabel info = new JLabel("Automanager  •  Sistema de Gestão  •  " + LocalDate.now());
        info.setForeground(new Color(80, 110, 160));
        info.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        rodape.add(info, BorderLayout.WEST);
        return rodape;
    }

    private void estilizarTabela(JTable t) {
        t.setBackground(new Color(18, 30, 55));
        t.setForeground(COR_BRANCO);
        t.setRowHeight(28);
        t.setGridColor(new Color(30, 60, 110));
        t.setShowVerticalLines(false);
        t.setSelectionBackground(COR_BOTAO);
        t.setSelectionForeground(COR_BRANCO);
        t.getTableHeader().setBackground(new Color(10, 20, 40));
        t.getTableHeader().setForeground(COR_TEXTO_SEC);
        t.getTableHeader().setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(30, 60, 120)));
        t.setAutoCreateRowSorter(true);

        // Renderer alternado para as linhas
        t.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(
                    JTable tbl, Object v, boolean sel, boolean foc, int row, int col) {
                super.getTableCellRendererComponent(tbl, v, sel, foc, row, col);
                setBackground(sel ? COR_BOTAO : (row % 2 == 0 ? new Color(18, 30, 55) : new Color(22, 38, 68)));
                setForeground(COR_BRANCO);
                setBorder(new EmptyBorder(0, 8, 0, 8));
                return this;
            }
        });
    }

    private void configurarColunaMoeda(JTable t, int col) {
        NumberFormat fmt = NumberFormat.getCurrencyInstance(new Locale("pt", "BR"));
        t.getColumnModel().getColumn(col).setCellRenderer(new DefaultTableCellRenderer() {
            @Override protected void setValue(Object v) {
                setText(v instanceof Number ? fmt.format(((Number) v).doubleValue()) : (v == null ? "" : v.toString()));
                setForeground(new Color(100, 220, 140));
                setBackground(new Color(18, 30, 55));
                setHorizontalAlignment(SwingConstants.RIGHT);
            }
        });
    }

    // ── Diálogos auxiliares ───────────────────────────────────────────
    private String pedirTexto(String mensagem, String valorAtual) {
        return (String) JOptionPane.showInputDialog(this, mensagem, "Entrada",
                JOptionPane.PLAIN_MESSAGE, null, null, valorAtual);
    }

    private String pedirOpcao(String mensagem, String[] opcoes, String padrao) {
        return (String) JOptionPane.showInputDialog(this, mensagem, "Seleção",
                JOptionPane.QUESTION_MESSAGE, null, opcoes, padrao);
    }

    private void aviso(String msg) {
        JOptionPane.showMessageDialog(this, msg, "Atenção", JOptionPane.WARNING_MESSAGE);
    }

    private void sucesso(String msg) {
        JOptionPane.showMessageDialog(this, msg, "Sucesso", JOptionPane.INFORMATION_MESSAGE);
    }

    private void erro(String msg) {
        JOptionPane.showMessageDialog(this, msg, "Erro", JOptionPane.ERROR_MESSAGE);
    }

    private boolean confirmar(String msg) {
        return JOptionPane.showConfirmDialog(this, msg, "Confirmar",
                JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION;
    }

    private double parseNumeroBr(String texto) {
        String v = texto == null ? "" : texto.trim();
        if (v.isEmpty()) throw new NumberFormatException("valor vazio");

        // Aceita "37.000", "37,000", "37000.50", "37.000,50", "R$ 37.000,50"
        v = v.replace("R$", "").replace(" ", "");
        if (v.contains(",") && v.contains(".")) {
            v = v.replace(".", "").replace(",", ".");
        } else if (v.matches("\\d{1,3}(\\.\\d{3})+")) {
            v = v.replace(".", "");
        } else if (v.contains(",")) {
            v = v.replace(",", ".");
        }
        return Double.parseDouble(v);
    }
}
