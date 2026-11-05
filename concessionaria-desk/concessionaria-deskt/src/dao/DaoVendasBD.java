package dao;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

import entidade.Vendas;

public class DaoVendasBD implements DaoVenda {

    /**
     * Registra a venda e automaticamente marca o carro como "Vendido".
     * Ambas as operações ocorrem na mesma transação — ou as duas salvam, ou nenhuma.
     */
    @Override
    public void registrarVenda(Vendas venda) throws SQLException {
        String sqlVenda  = "INSERT INTO vendas (id_carro, id_funcionario, valor_venda, comissao, data_venda) VALUES (?,?,?,?,?)";
        String sqlStatus = "UPDATE carros SET status = 'Vendido' WHERE id = ? AND status = 'Disponível'";

        Connection conn = null;
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false); // inicia transação

            try (PreparedStatement pVenda = conn.prepareStatement(sqlVenda)) {
                pVenda.setInt(1, venda.getIdCarro());
                pVenda.setInt(2, venda.getIdFuncionario());
                pVenda.setDouble(3, venda.getValorvenda());
                pVenda.setDouble(4, venda.getComissao());
                pVenda.setDate(5, Date.valueOf(venda.getDataVenda()));
                pVenda.executeUpdate();
            }

            int carrosAtualizados;
            try (PreparedStatement pStatus = conn.prepareStatement(sqlStatus)) {
                pStatus.setInt(1, venda.getIdCarro());
                carrosAtualizados = pStatus.executeUpdate();
            }

            if (carrosAtualizados == 0) {
                throw new SQLException("Carro não está mais disponível para venda.");
            }

            conn.commit(); // confirma transação

        } catch (SQLException e) {
            if (conn != null) conn.rollback(); // desfaz se algo falhar
            throw e;
        } finally {
            if (conn != null) conn.close();
        }
    }

    @Override
    public List<Vendas> listarVendas() {
        List<Vendas> vendas = new ArrayList<>();
        String sql = "SELECT * FROM vendas ORDER BY data_venda DESC";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {

            while (rs.next()) {
                Date data = rs.getDate("data_venda");
                vendas.add(new Vendas(
                    rs.getInt("id"),
                    rs.getInt("id_carro"),
                    rs.getInt("id_funcionario"),
                    rs.getDouble("valor_venda"),
                    rs.getDouble("comissao"),
                    data == null ? null : data.toLocalDate()
                ));
            }

        } catch (SQLException e) {
            System.err.println("Erro ao listar vendas: " + e.getMessage());
        }

        return vendas;
    }

    @Override
    public void update(Vendas venda) throws SQLException {
        if (venda.getDataVenda() == null) throw new SQLException("A venda precisa ter uma data.");
        String sql = "UPDATE vendas SET id_funcionario=?, valor_venda=?, comissao=?, data_venda=? WHERE id=? AND id_carro=?";

        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement ps = conn.prepareStatement("SELECT id_carro FROM vendas WHERE id=? FOR UPDATE")) {
                    ps.setInt(1, venda.getIdVenda());
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) throw new SQLException("Venda não encontrada.");
                        if (rs.getInt("id_carro") != venda.getIdCarro())
                            throw new SQLException("Não é permitido trocar o veículo de uma venda ativa.");
                    }
                }
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setInt(1, venda.getIdFuncionario());
                    ps.setDouble(2, venda.getValorvenda());
                    ps.setDouble(3, venda.getComissao());
                    ps.setDate(4, Date.valueOf(venda.getDataVenda()));
                    ps.setInt(5, venda.getIdVenda());
                    ps.setInt(6, venda.getIdCarro());
                    ps.executeUpdate();
                }
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        }
    }

    @Override
    public void deleteById(int id) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                Integer idCarro = null;
                try (PreparedStatement ps = conn.prepareStatement("SELECT id_carro FROM vendas WHERE id=? FOR UPDATE")) {
                    ps.setInt(1, id);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) idCarro = rs.getInt("id_carro");
                    }
                }
                if (idCarro == null) { conn.commit(); return; }
                try (PreparedStatement ps = conn.prepareStatement("DELETE FROM vendas WHERE id=?")) {
                    ps.setInt(1, id);
                    ps.executeUpdate();
                }
                // Exclusão representa cancelamento; só devolve o carro se não restar outra venda ativa.
                try (PreparedStatement ps = conn.prepareStatement("""
                        UPDATE carros SET status='Disponível'
                        WHERE id=? AND NOT EXISTS (SELECT 1 FROM vendas WHERE id_carro=?)
                        """)) {
                    ps.setInt(1, idCarro);
                    ps.setInt(2, idCarro);
                    ps.executeUpdate();
                }
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        }
    }
}
